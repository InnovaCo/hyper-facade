package eu.inn.facade.http

import java.io.ByteArrayOutputStream

import akka.actor._
import akka.util.ByteString
import eu.inn.binders.dynamic.Text
import eu.inn.facade.events.{SubscriptionActor, SubscriptionsManager}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.{HttpServiceActor, Route}

class WsRestWorker(val serverConnection: ActorRef,
                   workerRoutes: WsRestRoutes,
                   hyperBus: HyperBus,
                   subscriptionManager: SubscriptionsManager,
                   clientAddress: String) extends
  HttpServiceActor
  with websocket.WebSocketServerWorker
  with ActorLogging {

  var isConnectionTerminated = false
  var remoteAddress = clientAddress
  var request: Option[HttpRequest] = None

  override def preStart(): Unit = {
    super.preStart()
    serverConnection ! Http.Register(context.self)
    context.watch(serverConnection)
    if (log.isDebugEnabled) {
      log.debug(s"New connection with $serverConnection/$remoteAddress")
    }
  }

  // order is really important, watchConnection should be before httpRequests, otherwise there is a memory leak
  override def receive = watchConnection orElse handshaking orElse httpRequests

  def watchConnection: Receive = {
    case handshakeRequest @ websocket.HandshakeRequest(state) => {
      state match {
        case wsContext: websocket.HandshakeContext =>
          request = Some(wsContext.request)

          // todo: this is dangerous, network infrastructure should guarantee that http_x_forwarded_for is always overridden at server-side
          remoteAddress = wsContext.request.headers.find(_.is("http_x_forwarded_for")).map(_.value).getOrElse(clientAddress)
        case _ =>
      }
      handshaking(handshakeRequest)
    }

    case ev: Http.ConnectionClosed =>
      if (log.isDebugEnabled) {
        log.debug(s"Connection with $serverConnection/$remoteAddress is closing")
      }
      context.stop(serverConnection)

    case Terminated(`serverConnection`) ⇒ {
      if (log.isDebugEnabled) {
        log.debug(s"Connection with $serverConnection/$remoteAddress is terminated")
      }
      context.stop(context.self)
      isConnectionTerminated = true
    }
  }

  def businessLogic: Receive = {
    case message: Frame ⇒
      val dynamicRequest: Option[DynamicRequest] =
        try {
          Some(DynamicRequest(message.payload.iterator.asInputStream))
        }
        catch {
          case t: Throwable ⇒
            val msg = message.payload.utf8String
            val msgShort = msg.substring(0, Math.min(msg.length, 240))
            log.warning(s"Can't deserialize websocket message '$msg' from ${sender()}/$remoteAddress. $t")
            None
        }

      dynamicRequest.foreach(processRequest)

    case x: FrameCommandFailed =>
      log.error(s"Frame command $x failed from ${sender()}/$remoteAddress")

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
    if (isConnectionTerminated) {
      log.warning(s"Can't send message $message to $serverConnection/$remoteAddress: connection was terminated")
      false
    }
    else {
      val frame: Option[Frame] =
        try {
          val ba = new ByteArrayOutputStream()
          message.serialize(ba)
          Some(TextFrame(ByteString(ba.toByteArray)))
        }
        catch {
          case t: Throwable ⇒
            log.error(t, s"Can't serialize $message to $serverConnection/$remoteAddress")
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
}

class WsRestRoutes(aroute: ⇒ Route) {
  def route: Route = aroute
}
