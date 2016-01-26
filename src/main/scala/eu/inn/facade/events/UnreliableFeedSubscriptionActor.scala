package eu.inn.facade.events

import akka.actor.{ActorRef, Props}
import akka.pattern.pipe
import eu.inn.binders.dynamic.Null
import eu.inn.facade.filter.model.Headers
import eu.inn.facade.http.RequestMapper
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import scaldi.Injector

class UnreliableFeedSubscriptionActor(websocketWorker: ActorRef,
                                      hyperBus: HyperBus,
                                      subscriptionManager: SubscriptionsManager)
                                      (implicit inj: Injector)
  extends SubscriptionActor(websocketWorker, hyperBus, subscriptionManager) {

  override def process: Receive = super.process orElse {
    // Received event from HyperBus. Should be sent to client
    case event @ DynamicRequest(RequestHeader(_, "post", _, _, _), body) ⇒
      import context._

      subscriptionRequest foreach { request ⇒
        filterOut(request, event) map {
          case (headers: Headers, body: DynamicBody) ⇒ RequestMapper.toDynamicRequest(headers, body)
        } pipeTo websocketWorker
      }
  }

  override def fetchAndReplyWithResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory): Unit = {
    import context._

    // todo: update front correlationId <> back correlationId!
    val resourceUri = ramlConfig.resourceStateUri(request.url)
    hyperBus <~ DynamicGet(resourceUri, DynamicBody(EmptyBody.contentType, Null)) flatMap {
      case response: Response[DynamicBody] ⇒
        filterOut(request, response)
    } recover {
      case t: Throwable ⇒ exceptionToResponse(t)
    } map {
      case (headers: Headers, dynamicBody: DynamicBody) ⇒ RequestMapper.toDynamicResponse(headers, dynamicBody)
    } pipeTo websocketWorker
  }
}

object UnreliableFeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperBus: HyperBus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new UnreliableFeedSubscriptionActor(
    websocketWorker,
    hyperBus,
    subscriptionManager))
}
