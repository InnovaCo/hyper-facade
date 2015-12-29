package eu.inn.facade.events

import akka.actor.{Actor, ActorLogging, ActorRef}
import eu.inn.binders.dynamic.Null
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.Topic
import eu.inn.hyperbus.{HyperBus, IdGenerator}

import scala.util.matching.Regex

class SubscriptionActor(websocketWorker: ActorRef,
                        hyperBus: HyperBus,
                        subscriptionManager: SubscriptionsManager) extends Actor with ActorLogging {

  var subscriptionId: Option[String] = None
  override def receive: Receive = {
    case request @ DynamicRequest(RequestHeader(url, "subscribe", _, messageId, correlationId), body) ⇒
      val finalCorrelationId = correlationId.getOrElse(messageId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(finalCorrelationId)

      if (SubscriptionActor.urlStatusMonitorRegex.findFirstIn(url).isDefined) {
        subscriptionId = Some(subscriptionManager.subscribe(Topic(url), websocketWorker, finalCorrelationId)) // todo: Topic logic/raml
        //context.become(subscribed(request))
        fetchAndReplyWithResource(request)
      }
      else {
        websocketWorker ! NotFound(ErrorBody("not-found", Some(s"$url not found")))
      }

    case request @ DynamicRequest(RequestHeader(_, "unsubscribe", _, _, _), _) ⇒
      context.stop(self)

    case other @ DynamicRequest(_,_) ⇒
      log.error(s"Invalid request received on $self: $other")
      context.stop(self)
  }

  override def postStop(): Unit = {
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
  }

  private def fetchAndReplyWithResource(request: DynamicRequest)(implicit mvx: MessagingContextFactory) = {
    import akka.pattern.pipe
    import context._

    // todo: update front correlationId <> back correlationId!
    hyperBus <~ DynamicGet(request.url, DynamicBody(EmptyBody.contentType, Null)) recover {
      case e: Response[Body] ⇒ e
      case t: Throwable ⇒ exceptionToResponse(t)
    } pipeTo websocketWorker
  }

  private def exceptionToResponse(t: Throwable)(implicit mcf: MessagingContextFactory): Response[Body] = {
    val errorId = IdGenerator.create()
    log.error(t, "Can't handle request. #" + errorId)
    InternalServerError(ErrorBody("unhandled-exception", Some(t.getMessage + " #"+errorId), errorId = errorId))
  }
}

private[facade] object SubscriptionActor {
  val urlStatusMonitorRegex = new Regex("/users/(.*)/status-monitor")
}
