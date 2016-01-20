package eu.inn.facade.events

import akka.actor.ActorRef
import eu.inn.binders.dynamic.Null
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader

class UnreliableFeedSubscriptionActor(websocketWorker: ActorRef,
                        hyperBus: HyperBus,
                        subscriptionManager: SubscriptionsManager)
  extends SubscriptionActor(websocketWorker, hyperBus, subscriptionManager) {

  def process: Receive = {
    case request @ DynamicRequest(RequestHeader(url, "subscribe", _, messageId, correlationId), body) ⇒
      subscribe(request, websocketWorker)
      fetchAndReplyWithResource(request.url)
  }

  override def fetchAndReplyWithResource(url: String)(implicit mvx: MessagingContextFactory): Unit = {
    import akka.pattern.pipe
    import context._

    // todo: update front correlationId <> back correlationId!
    hyperBus <~ DynamicGet(url.replace("{content}/events", "resource"), DynamicBody(EmptyBody.contentType, Null)) map {
      case response: Response[DynamicBody] ⇒ response
    } recover {
      case t: Throwable ⇒ exceptionToResponse(t)
    } pipeTo websocketWorker
  }
}
