package eu.inn.facade.events

import akka.actor.{ActorRef, Props}
import akka.pattern.pipe
import eu.inn.facade.filter.model.TransitionalHeaders
import eu.inn.facade.http.RequestMapper
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import scaldi.Injector

class UnreliableFeedSubscriptionActor(websocketWorker: ActorRef,
                                      hyperBus: HyperBus,
                                      subscriptionManager: SubscriptionsManager)
                                      (implicit inj: Injector)
  extends SubscriptionActor(websocketWorker, hyperBus, subscriptionManager) {

  override def process(event: DynamicRequest): Unit = {
    import context._

    subscriptionRequest foreach { request ⇒
      filterOut(request, event) map {
        case (headers: TransitionalHeaders, body: DynamicBody) ⇒ RequestMapper.toDynamicRequest(headers, body)
      } pipeTo websocketWorker
    }
  }
  override def fetchAndReplyWithResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory): Unit = {
    import context._

    hyperBus <~ DynamicRequest(resourceStateUri(request.uri), request.body, request.headers) flatMap {
      case response: Response[DynamicBody] ⇒
        filterOut(request, response)
    } recover {
      case t: Throwable ⇒ exceptionToResponse(t)
    } map {
      case (headers: TransitionalHeaders, dynamicBody: DynamicBody) ⇒ RequestMapper.toDynamicResponse(headers, dynamicBody)
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
