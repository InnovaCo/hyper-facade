package eu.inn.facade.events

import akka.actor.ActorRef
import eu.inn.binders.dynamic.Null
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard.{DynamicGet, EmptyBody}
import eu.inn.hyperbus.serialization.RequestHeader

import scala.collection.mutable

class ReliableFeedSubscriptionActor(websocketWorker: ActorRef,
                                    hyperBus: HyperBus,
                                    subscriptionManager: SubscriptionsManager)
  extends SubscriptionActor(websocketWorker, hyperBus, subscriptionManager) {

  val pendingEvents: mutable.Queue[DynamicRequest] = mutable.Queue()
  var resourceStateFetched = false
  var lastRevisionId: Long = 0
  var resubscriptionCount: Int = 0

  def process: Receive = {
    case request @ DynamicRequest(RequestHeader(url, "subscribe", _, messageId, correlationId), body) ⇒
      subscribe(request, self)
      fetchAndReplyWithResource(request.url)

    case request @ DynamicRequest(RequestHeader(_, "post", _, _, _), body) ⇒
      if (!resourceStateFetched) {
        pendingEvents += request
      }
      else {
        sendEvent(request)
      }
  }

  override def fetchAndReplyWithResource(url: String)(implicit mvx: MessagingContextFactory) = {
    import context._

    // todo: update front correlationId <> back correlationId!
    val resourceStateFuture = hyperBus <~ DynamicGet(url.replace("{content}/events", "resource"), DynamicBody(EmptyBody.contentType, Null)) map {
      case response: Response[DynamicBody] ⇒
        lastRevisionId = response.body.content.revisionId[Long]
        response
    } recover {
      case t: Throwable ⇒ exceptionToResponse(t)
    }
    resourceStateFuture onSuccess {
      case response: Response[DynamicBody] ⇒
        websocketWorker ! response
        while (pendingEvents.nonEmpty) {
          val request = pendingEvents.dequeue()
          sendEvent(request)
        }
        resourceStateFetched = true
    }
  }

  private def sendEvent(request: DynamicRequest): Unit = {
    val revisionId = request.body.content.revisionId[Long]
    if (revisionId == lastRevisionId + 1) {
      websocketWorker ! request
      lastRevisionId = revisionId
    } else if (revisionId > lastRevisionId + 1) {
      resubscribe(request)
    }
    // if revisionId <= lastRevisionId -- just ignore this event
  }

  private def resubscribe(request: DynamicRequest): Unit = {
    unsubscribe()
    resubscriptionCount += 1
    if (resubscriptionCount > 10) context.stop(self) // todo: inject from config
    lastRevisionId = 0
    pendingEvents.clear()
    fetchAndReplyWithResource(request.url)
    subscribe(request, self)
  }
}
