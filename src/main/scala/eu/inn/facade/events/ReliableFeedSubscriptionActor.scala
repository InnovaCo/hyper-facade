package eu.inn.facade.events

import akka.actor.{ActorRef, Props}
import com.typesafe.config.Config
import eu.inn.facade.filter.model.{DynamicRequestHeaders, TransitionalHeaders}
import eu.inn.facade.http.RequestMapper
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import scaldi.{Injectable, Injector}

import scala.collection.mutable

class ReliableFeedSubscriptionActor(websocketWorker: ActorRef,
                                    hyperBus: HyperBus,
                                    subscriptionManager: SubscriptionsManager)
                                    (implicit inj: Injector)
  extends SubscriptionActor(websocketWorker, hyperBus, subscriptionManager)
  with Injectable {

  val pendingEvents: mutable.Queue[DynamicRequest] = mutable.Queue()
  val maxResubscriptionsCount = inject[Config].getInt("inn.facade.maxResubscriptionsCount")

  var resourceStateFetched = false
  var lastRevisionId: Long = 0
  var resubscriptionCount: Int = 0
  var initialRequest: Option[DynamicRequest] = None

  override def process(event: DynamicRequest): Unit = {
      if (!resourceStateFetched)
        pendingEvents += event
      else
        sendEvent(event)
  }

  override def fetchAndReplyWithResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory) = {
    import context._

    hyperBus <~ DynamicRequest(resourceStateUri(request.uri), request.body, request.headers) flatMap {
      case response: Response[DynamicBody] ⇒
        lastRevisionId = response.headers(DynamicRequestHeaders.REVISION).head.toLong
        filterOut(request, response)
    } recover {
      case t: Throwable ⇒ exceptionToResponse(t)
    } map {
      case (headers: TransitionalHeaders, dynamicBody: DynamicBody) ⇒
        val response = RequestMapper.toDynamicResponse(headers, dynamicBody)
        websocketWorker ! response
        sendQueuedMessages()
        resourceStateFetched = true
    }
  }

  def sendQueuedMessages(): Unit = {
    while (pendingEvents.nonEmpty) {
      sendEvent(pendingEvents.dequeue())
    }
  }

  private def sendEvent(event: DynamicRequest): Unit = {
    import akka.pattern.pipe
    import context._

    val revisionId = event.headers(DynamicRequestHeaders.REVISION).head.toLong
    subscriptionRequest foreach { request ⇒
      if (revisionId == lastRevisionId + 1)
        filterOut(request, event) map {
          case (headers: TransitionalHeaders, body: DynamicBody) ⇒
            lastRevisionId = revisionId
            RequestMapper.toDynamicRequest(headers, body)
        } pipeTo websocketWorker
      else if (revisionId > lastRevisionId + 1) resubscribe(request)
          // if revisionId <= lastRevisionId -- just ignore this event
    }
  }

  private def resubscribe(request: DynamicRequest): Unit = {
    unsubscribe()
    resubscriptionCount += 1
    if (resubscriptionCount > maxResubscriptionsCount)
      context.stop(self)
    lastRevisionId = -1
    pendingEvents.clear()
    filterAndSubscribe(request)
  }
}

object ReliableFeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperBus: HyperBus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new ReliableFeedSubscriptionActor(
  websocketWorker,
  hyperBus,
  subscriptionManager))
}
