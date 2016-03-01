package eu.inn.facade.events

import akka.actor.{Actor, ActorLogging, ActorRef}
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model.{DynamicRequestHeaders, TransitionalHeaders}
import eu.inn.facade.http.RequestMapper
import eu.inn.facade.raml.{Method, RamlConfig}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.TextMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

abstract class SubscriptionActor(websocketWorker: ActorRef,
                                  hyperBus: HyperBus,
                                  subscriptionManager: SubscriptionsManager)
                                  (implicit inj: Injector)
  extends Actor
  with ActorLogging
  with Injectable {

  val filterChainComposer = inject[FilterChainFactory]
  val ramlConfig = inject[RamlConfig]

  var subscriptionId: Option[String] = None
  var subscriptionRequest: Option[DynamicRequest] = None

  def fetchAndReplyWithResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory): Unit
  def process(request: DynamicRequest): Unit

  override def receive: Receive = {

    case request : DynamicRequest ⇒
      request.headers.get(Header.METHOD) match {

        case Some(Seq("subscribe")) ⇒
          subscriptionRequest = Some(request)
          filterAndSubscribe(request)

        case Some(Seq("unsubscribe")) ⇒
          context.stop(self)

        // This request received from backend sevice via HyperBus. Will be sent to client
        case _ ⇒
          process(request)
      }


    case other ⇒
      log.error(s"Invalid request received on $self: $other")
      context.stop(self)
  }

  override def postStop(): Unit = {
    unsubscribe()
  }

  def unsubscribe(): Unit = {
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
  }

  def filterAndSubscribe(request: DynamicRequest): Unit = {
    filterIn(request) onComplete {
      case Success((headers, body)) ⇒
        if (headers.hasStatusCode) {
          // it means that request didn't pass filters and declined with error
          websocketWorker ! RequestMapper.toDynamicResponse(headers, body)
        } else {
          val filteredRequest = RequestMapper.toDynamicRequest(headers, body)
          subscribe(filteredRequest)
          implicit val mvx = MessagingContextFactory.withCorrelationId(serverCorrelationId(filteredRequest))
          fetchAndReplyWithResource(filteredRequest)
        }
    }
  }

  def subscribe(request: DynamicRequest): Unit = {
    val finalCorrelationId = RequestMapper.correlationId(request.headers)
    subscriptionId = Some(subscriptionManager.subscribe(resourceFeedUri(request.uri), self, finalCorrelationId))
  }

  def serverCorrelationId(request: DynamicRequest): String = {
    val clientCorrelationId = RequestMapper.correlationId(request.headers)
    clientCorrelationId + self.path
  }

  def clientCorrelationId(requestCorrelationId: Option[String], messageId: String): String = {
    requestCorrelationId.getOrElse(messageId)
  }

  def filterIn(dynamicRequest: DynamicRequest): Future[(TransitionalHeaders, DynamicBody)] = {
    val uriPattern = dynamicRequest.uri.pattern.specific.toString
    val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
    val contentType = headers.singleValueHeader(DynamicRequestHeaders.CONTENT_TYPE)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.inputFilterChain(uriPattern, Method.POST, contentType).applyFilters(headers, dynamicBody)
  }

  def filterOut(dynamicRequest: DynamicRequest, responseEvent: DynamicRequest): Future[(TransitionalHeaders, DynamicBody)] = {
    val uriPattern = dynamicRequest.uri.pattern.specific.toString
    val (headers, dynamicBody) = RequestMapper.unfold(responseEvent)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.outputFilterChain(uriPattern, Method.POST).applyFilters(headers, dynamicBody)
  }

  def filterOut(request: DynamicRequest, response: Response[DynamicBody]): Future[(TransitionalHeaders, DynamicBody)] = {
    val statusCode = response.status
    val uriPattern = request.uri.pattern.specific.toString
    val body = response.body
    val headers = RequestMapper.extractResponseHeaders(statusCode, response.headers, response.messageId, response.correlationId)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.outputFilterChain(uriPattern, Method.POST).applyFilters(headers, body)
  }

  def exceptionToResponse(t: Throwable)(implicit mcf: MessagingContextFactory): Response[Body] = {
    val errorId = IdGenerator.create()
    log.error(t, "Can't handle request. #" + errorId)
    InternalServerError(ErrorBody("unhandled-exception", Some(t.getMessage + " #"+errorId), errorId = errorId))
  }

  def resourceStateUri(uri: Uri): Uri = {
    val resourceStateUriPattern = ramlConfig.resourceStateUri(uri.pattern.specific.toString)
    composeUri(resourceStateUriPattern, uri.args)
  }

  def resourceFeedUri(uri: Uri): Uri = {
    val resourceFeedUriPattern = ramlConfig.resourceFeedUri(uri.pattern.specific.toString)
    composeUri(resourceFeedUriPattern, uri.args)
  }

  def composeUri(pattern: String, args: Map[String, TextMatcher]): Uri = {
    val resourceUriArgs = args map {
      case (argName, argValue) ⇒ argName → argValue.toString
    }
    Uri(pattern, resourceUriArgs)
  }
}
