package eu.inn.facade.events

import akka.actor.{Actor, ActorLogging, ActorRef}
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model.{DynamicRequestHeaders, Headers}
import eu.inn.facade.http.RequestMapper
import eu.inn.facade.raml.{Method, RamlConfig}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard.{ErrorBody, InternalServerError}
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.Topic
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

  override def receive: Receive = process orElse interruptProcessing

  def process: Receive = {
    // Request for subscription to resource event feed
    case request @ DynamicRequest(RequestHeader(_, "subscribe", _, _, _), _) ⇒
      subscriptionRequest = Some(request)
      filterAndSubscribe(request)
  }

  def interruptProcessing: Receive = {
    case request @ DynamicRequest(RequestHeader(_, "unsubscribe", _, _, _), _) ⇒
      context.stop(self)

    case other @ DynamicRequest(_,_) ⇒
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
    request match {
      case DynamicRequest(RequestHeader(url, _, _, messageId, correlationId), _) ⇒
        val resourceFeedUri = ramlConfig.resourceFeedUri(url)
        val finalCorrelationId = clientCorrelationId(correlationId, messageId)
        subscriptionId = Some(subscriptionManager.subscribe(Topic(resourceFeedUri), self, finalCorrelationId))
    }
  }

  def serverCorrelationId(request: DynamicRequest): String = {
    request match {
      case DynamicRequest(RequestHeader(_, _, _, messageId, correlationId), _) ⇒
        clientCorrelationId(correlationId, messageId) + self.path
    }
  }

  def clientCorrelationId(requestCorrelationId: Option[String], messageId: String): String = {
    requestCorrelationId.getOrElse(messageId)
  }

  def filterIn(dynamicRequest: DynamicRequest): Future[(Headers, DynamicBody)] = {
    val url = dynamicRequest.url
    val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
    val contentType = headers.headers.get(DynamicRequestHeaders.CONTENT_TYPE)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.inputFilterChain(url, Method.POST, contentType).applyFilters(headers, dynamicBody)
  }

  def filterOut(dynamicRequest: DynamicRequest, responseEvent: DynamicRequest): Future[(Headers, DynamicBody)] = {
    val url = dynamicRequest.url
    val (headers, dynamicBody) = RequestMapper.unfold(responseEvent)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.outputFilterChain(url, Method.POST).applyFilters(headers, dynamicBody)
  }

  def filterOut(request: DynamicRequest, response: Response[DynamicBody]): Future[(Headers, DynamicBody)] = {
    val statusCode = response.status
    val url = request.url
    val body = response.body
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.outputFilterChain(url, Method.POST).applyFilters(Headers(Map(), Some(statusCode)), body)
  }

  def exceptionToResponse(t: Throwable)(implicit mcf: MessagingContextFactory): Response[Body] = {
    val errorId = IdGenerator.create()
    log.error(t, "Can't handle request. #" + errorId)
    InternalServerError(ErrorBody("unhandled-exception", Some(t.getMessage + " #"+errorId), errorId = errorId))
  }
}
