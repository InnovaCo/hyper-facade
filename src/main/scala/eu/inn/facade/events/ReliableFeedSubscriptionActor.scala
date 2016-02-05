package eu.inn.facade.events

import akka.actor.{ActorRef, Props}
import com.typesafe.config.Config
import eu.inn.binders.dynamic.Null
import eu.inn.facade.filter.model.Headers
import eu.inn.facade.http.RequestMapper
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard.{DynamicGet, EmptyBody}
import eu.inn.hyperbus.serialization.RequestHeader
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

  override def process: Receive = super.process orElse {
    // Received event from HyperBus. Should be sent to client
    case request @ DynamicRequest(RequestHeader(_, "post", _, _, _), body) ⇒
      if (!resourceStateFetched) pendingEvents += request
      else sendEvent(request)
  }

  override def fetchAndReplyWithResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory) = {
    import context._

    val resourceUri = ramlConfig.resourceStateUri(request.url)
    hyperBus <~ DynamicGet(resourceUri, DynamicBody(EmptyBody.contentType, Null)) flatMap {
      case response: Response[DynamicBody] ⇒
        lastRevisionId = response.body.content.revisionId[Long]
        filterOut(request, response)
    } recover {
      case t: Throwable ⇒ exceptionToResponse(t)
    } map {
      case (headers: Headers, dynamicBody: DynamicBody) ⇒
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

    val revisionId = event.body.content.revisionId[Long]
    subscriptionRequest foreach { request ⇒
      if (revisionId == lastRevisionId + 1)
        filterOut(request, event) map {
          case (headers: Headers, body: DynamicBody) ⇒
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
