package eu.inn.facade

import java.io.ByteArrayOutputStream

import akka.actor.{ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.util.ByteString
import eu.inn.binders.dynamic.Text
import eu.inn.facade.events.{SubscriptionActor, SubscriptionsManager}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import spray.can.websocket
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.routing.{HttpServiceActor, Route}

class WsRestWorker(val serverConnection: ActorRef,
                   workerRoutes: WsRestRoutes,
                   hyperBus: HyperBus,
                   subscriptionManager: SubscriptionsManager) extends
  HttpServiceActor with websocket.WebSocketServerWorker with ActorLogging {

  // order is really important, closeLogic should be before httpRequests, otherwise there is a memory leak
  override def receive = handshaking orElse closeLogic orElse httpRequests

  def businessLogic: Receive = {
    case message: Frame ⇒
      val dynamicRequest: Option[DynamicRequest] =
        try {
          Some(DynamicRequest(message.payload.iterator.asInputStream))
        }
        catch {
          case t: Throwable ⇒
            log.error(s"Can't deserialize websocket request from ${sender()}", t)
            None
        }

      dynamicRequest.foreach(processRequest)

    case x: FrameCommandFailed =>
      log.error(s"Frame command failed from: ${sender()}", x)

    case message: Message[Body] ⇒
      send(message)
  }

  def httpRequests: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      workerRoutes.route
    }
  }

  def processRequest(request: DynamicRequest) = request match {
    case request @ DynamicRequest(RequestHeader("/meta/ping", "ping", _, messageId, correlationId), _) ⇒ {
      val finalCorrelationId = correlationId.getOrElse(messageId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(finalCorrelationId)
      send(Ok(DynamicBody(Text("pong"))))
    }

    case request @ DynamicRequest(RequestHeader(_,_,_,messageId,correlationId), _) ⇒
      val key = correlationId.getOrElse(messageId)
      val actorName = "Subscr-" + key
      context.child(actorName) match {
        case Some(actor) ⇒ actor.forward(request)
        case None ⇒ context.actorOf(Props(classOf[SubscriptionActor], self, hyperBus, subscriptionManager), actorName) ! request
      }
  }

  def send(message: Message[Body]): Boolean = {
    val frame: Option[Frame] =
      try {
        val ba = new ByteArrayOutputStream()
        message.serialize(ba)
        Some(TextFrame(ByteString(ba.toByteArray)))
      }
      catch {
        case t: Throwable ⇒
          log.error(s"Can't serialize $message", t)
          None
      }

    frame.map { f ⇒
      send(f)
      true
    } getOrElse {
      false
    }
  }
}

class WsRestRoutes(aroute: ⇒ Route) {
  def route: Route = aroute
}
