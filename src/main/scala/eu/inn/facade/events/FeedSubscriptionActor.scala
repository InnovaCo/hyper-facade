package eu.inn.facade.events

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import com.typesafe.config.Config
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model
import eu.inn.facade.filter.model.TransitionalHeaders
import eu.inn.facade.http.RequestMapper
import eu.inn.facade.raml.{Method, RamlConfig}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperBus: HyperBus,
                            subscriptionManager: SubscriptionsManager)
                           (implicit inj: Injector)
  extends Actor
  with ActorLogging
  with Injectable {

  val filterChainComposer = inject[FilterChainFactory]
  val ramlConfig = inject[RamlConfig]
  val pendingEvents = new ConcurrentLinkedQueue[DynamicRequest]
  val maxResubscriptionsCount = inject[Config].getInt("inn.facade.maxResubscriptionsCount")
  val feedState = new AtomicReference[FeedState](FeedState())

  var subscriptionId = new AtomicReference[Option[String]](None)
  var subscriptionRequest = new AtomicReference[Option[DynamicRequest]](None)

  override def receive: Receive = {

    case request : DynamicRequest ⇒
      request.headers.get(Header.METHOD) match {

        case Some(Seq("subscribe")) ⇒
          subscriptionRequest.set(Some(request))
          filterAndSubscribe(request)

        case Some(Seq("unsubscribe")) ⇒
          context.stop(self)

        // This request received from backend sevice via HyperBus. Will be sent to client
        case _ ⇒
          processEvent(request)
      }

    case other ⇒
      log.error(s"Invalid request received on $self: $other")
      context.stop(self)
  }

  override def postStop(): Unit = {
    unsubscribe()
  }

  def processEvent(event: DynamicRequest): Unit = {
    val feedStateSnapshot = feedState.get
    val resourceStateFetched = feedStateSnapshot.resourceStateFetched
    event.headers.get(Header.REVISION) match {
      case Some(revision) ⇒
        if (!resourceStateFetched || !pendingEvents.isEmpty) {
          pendingEvents.add(event)
          log.info(s"event $event is enqueued because resource state is not fetched yet")
        }
        else sendEvent(event)

      case None ⇒
        if (resourceStateFetched) {
          subscriptionRequest.get foreach { request ⇒
            filterEvent(request, event) map {
              case (headers: TransitionalHeaders, body: DynamicBody) ⇒ RequestMapper.toDynamicRequest(headers, body)
            } pipeTo websocketWorker
          }
        } else log.warning(s"event $event will be dropped because resource state is not fetched yet")
    }
  }

  def filterAndSubscribe(request: DynamicRequest): Unit = {
    val resourceUri = ramlConfig.resourceUri(request.uri.pattern.specific)
    val preparedRequest = DynamicRequest(resourceUri, request.body, request.headers)
    filterRequest(preparedRequest) onComplete {
      case Success((headers, body)) ⇒
        if (headers.hasStatusCode) {
          // it means that request didn't pass filters and declined with error
          websocketWorker ! RequestMapper.toDynamicResponse(headers, body)
        } else {
          val filteredRequest = RequestMapper.toDynamicRequest(headers, body)
          feedState.set(FeedState())
          subscribe(filteredRequest)
          implicit val mvx = MessagingContextFactory.withCorrelationId(serverCorrelationId(filteredRequest))
          fetchResource(filteredRequest)
        }
    }
  }

  def fetchResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory): Unit = {
    val feedStateSnapshot = feedState.get
    val updatedRequest = DynamicRequest(request.uri, request.body, request.headers)
    hyperBus <~ updatedRequest flatMap {
      case response: Response[DynamicBody] ⇒ filterResponse(updatedRequest, response)
    } map {
      case (headers: TransitionalHeaders, dynamicBody: DynamicBody) ⇒
        val response = RequestMapper.toDynamicResponse(headers, dynamicBody)
        headers.headerOption(model.FacadeHeaders.CLIENT_REVISION_ID) match {
          case Some(revisionIdStr) ⇒
            val revisionId = revisionIdStr.toLong
            val resourceStateFetched = true
            val reliableFeed = true
            val resubscriptionCount = feedStateSnapshot.resubscriptionCount
            val updated = feedState.compareAndSet(feedStateSnapshot, FeedState(resourceStateFetched, reliableFeed, revisionId, resubscriptionCount))
            if (updated) {
              websocketWorker ! response
              sendQueuedEvents()
            }

          case None ⇒
            val reliableFeed = false
            val resourceStateFetched = true
            val updated = feedState.compareAndSet(feedStateSnapshot, FeedState(resourceStateFetched, reliableFeed, 0L, 0))
            if (updated) websocketWorker ! response
        }
    } recover {
      case t: Throwable ⇒
        val response = RequestMapper.exceptionToResponse(t)
        websocketWorker ! response
        log.error(s"Service answered with error #${response.body.asInstanceOf[ErrorBody].errorId}. Stopping actor")
        context.stop(self)
    }
  }

  def sendQueuedEvents(): Unit = {
    while (!pendingEvents.isEmpty) {
      sendEvent(pendingEvents.poll())
    }
  }

  private def sendEvent(event: DynamicRequest): Unit = {
    val feedStateSnapshot = feedState.get
    val revisionId = event.headers(Header.REVISION).head.toLong
    subscriptionRequest.get foreach { request ⇒
      if (revisionId == feedStateSnapshot.lastRevisionId + 1)
        filterEvent(request, event) map {
          case (headers: TransitionalHeaders, body: DynamicBody) ⇒
            val lastRevisionId = revisionId
            val resubscriptionCount = feedStateSnapshot.resubscriptionCount
            val updated = feedState.compareAndSet(feedStateSnapshot, FeedState(true, true, lastRevisionId, resubscriptionCount))
            if (updated) websocketWorker ! RequestMapper.toDynamicRequest(headers, body)
        }
      else if (revisionId > feedStateSnapshot.lastRevisionId + 1) resubscribe(request)
      // if revisionId <= lastRevisionId -- just ignore this event
    }
  }

  private def resubscribe(request: DynamicRequest): Unit = {
    unsubscribe()
    val resubscriptionCount = feedState.get.lastRevisionId + 1
    if (resubscriptionCount > maxResubscriptionsCount)
      context.stop(self)
    val lastRevisionId = -1
    pendingEvents.clear()
    filterAndSubscribe(request)
  }

  def unsubscribe(): Unit = {
    subscriptionId.get.foreach(subscriptionManager.off)
    subscriptionId.set(None)
  }

  def subscribe(request: DynamicRequest): Unit = {
    val finalCorrelationId = RequestMapper.correlationId(request.headers)
    subscriptionId.set(Some(subscriptionManager.subscribe(request.uri, self, finalCorrelationId)))
  }

  def serverCorrelationId(request: DynamicRequest): String = {
    val clientCorrelationId = RequestMapper.correlationId(request.headers)
    clientCorrelationId + self.path
  }

  def filterRequest(dynamicRequest: DynamicRequest): Future[(TransitionalHeaders, DynamicBody)] = {
    val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
    val contentType = headers.headerOption(Header.CONTENT_TYPE)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.inputFilterChain(dynamicRequest.uri, Method.POST, contentType).applyFilters(headers, dynamicBody)
  }

  def filterEvent(dynamicRequest: DynamicRequest, responseEvent: DynamicRequest): Future[(TransitionalHeaders, DynamicBody)] = {
    val (headers, dynamicBody) = RequestMapper.unfold(responseEvent)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.outputFilterChain(dynamicRequest.uri, Method.POST).applyFilters(headers, dynamicBody)
  }

  def filterResponse(request: DynamicRequest, response: Response[DynamicBody]): Future[(TransitionalHeaders, DynamicBody)] = {
    val statusCode = response.status
    val body = response.body
    val headers = RequestMapper.extractResponseHeaders(statusCode, response.headers, response.messageId, response.correlationId)
    // Method is POST, because it's not an HTTP request but DynamicRequest via websocket, so there is no
    // HTTP method and we treat all websocket requests as sent with POST method
    filterChainComposer.outputFilterChain(request.uri, Method.POST).applyFilters(headers, body)
  }
}

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperBus: HyperBus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperBus,
    subscriptionManager))
}
