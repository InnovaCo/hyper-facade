package eu.inn.facade.events

import akka.actor.{ActorLogging, Actor, ActorRef}
import eu.inn.hyperbus.model.standard.{ErrorBody, InternalServerError}
import eu.inn.hyperbus.{IdGenerator, HyperBus}
import eu.inn.hyperbus.model.{Body, Response, MessagingContextFactory, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.Topic

abstract class SubscriptionActor(websocketWorker: ActorRef,
                        hyperBus: HyperBus,
                        subscriptionManager: SubscriptionsManager) extends Actor with ActorLogging {

  var subscriptionId: Option[String] = None

  def fetchAndReplyWithResource(url: String)(implicit mvx: MessagingContextFactory): Unit
  def process: Receive

  override def receive: Receive = process orElse interruptProcessing

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

  def subscribe(request: DynamicRequest, replyTo: ActorRef): Unit = {
    request match {
      case DynamicRequest(RequestHeader(url, _, _, messageId, correlationId), _) ⇒
        val finalCorrelationId = correlationId.getOrElse(messageId)
        implicit val mvx = MessagingContextFactory.withCorrelationId(finalCorrelationId)
        subscriptionId = Some(subscriptionManager.subscribe(Topic(url), replyTo, finalCorrelationId))  // todo: Topic logic/raml
    }
  }

  def exceptionToResponse(t: Throwable)(implicit mcf: MessagingContextFactory): Response[Body] = {
    val errorId = IdGenerator.create()
    log.error(t, "Can't handle request. #" + errorId)
    InternalServerError(ErrorBody("unhandled-exception", Some(t.getMessage + " #"+errorId), errorId = errorId))
  }
}
