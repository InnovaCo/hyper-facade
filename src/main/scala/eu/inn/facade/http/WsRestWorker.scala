package eu.inn.facade.http

import akka.actor._
import eu.inn.binders.value.Text
import eu.inn.facade.events.{FeedSubscriptionActor, SubscriptionsManager}
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.{FacadeHeaders, FacadeMessage, FacadeRequest, FacadeResponse}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.{Hyperbus, IdGenerator}
import scaldi.{Injectable, Injector}
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.Frame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

class WsRestWorker(val serverConnection: ActorRef,
                   workerRoutes: WsRestRoutes,
                   hyperbus: Hyperbus,
                   subscriptionManager: SubscriptionsManager,
                   clientAddress: String)
                  (implicit inj: Injector)
  extends HttpServiceActor
  with websocket.WebSocketServerWorker
  with ActorLogging
  with Injectable {

  var isConnectionTerminated = false
  var remoteAddress = clientAddress
  var httpRequest: Option[HttpRequest] = None

  val filterChainComposer = inject[FilterChain]
  val ramlConfig = inject[RamlConfig]

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
    case handshakeRequest@websocket.HandshakeRequest(state) ⇒
      state match {
        case wsContext: websocket.HandshakeContext ⇒
          httpRequest = Some(wsContext.request)

          // todo: support Forwarded & by RFC 7239
          remoteAddress = wsContext.request.headers.find(_.is(FacadeHeaders.CLIENT_IP)).map(_.value).getOrElse(clientAddress)
        case _ ⇒
      }
      handshaking(handshakeRequest)

    case ev: Http.ConnectionClosed ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Connection with $serverConnection/$remoteAddress is closing")
      }
      context.stop(serverConnection)

    case Terminated(`serverConnection`) ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Connection with $serverConnection/$remoteAddress is terminated")
      }
      context.stop(context.self)
      isConnectionTerminated = true
  }

  def businessLogic: Receive = {
    case message: Frame ⇒
      try {
        val facadeRequest = FacadeRequest(message)
        // todo: add uri validation (for example this isn't valid uri here: https://ya.ru only path is allowed
        // todo: + headers for host & port
        // todo: same for http requests
        val uriPattern = facadeRequest.uri.pattern.specific
        val method = facadeRequest.method
        if (isPingRequest(uriPattern, method)) {
          pong(facadeRequest)
        }
        else {
          val requestWithClientIp = facadeRequest.copy(
            // todo: support Forwarded by RFC 7239
            headers = facadeRequest.headers + (FacadeHeaders.CLIENT_IP → Seq(remoteAddress))
          )
          processRequest(requestWithClientIp)
        }
      }
      catch {
        case t: Throwable ⇒
          val msg = message.payload.utf8String
          //          val msgShort = msg.substring(0, Math.min(msg.length, 240))
          log.warning(s"Can't deserialize websocket message '$msg' from ${sender()}/$remoteAddress. $t")
          t.printStackTrace()
          None
      }

    case x: FrameCommandFailed =>
      log.error(s"Frame command $x failed from ${sender()}/$remoteAddress")

    case message: FacadeMessage ⇒
      send(message)
  }

  def httpRequests: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      workerRoutes.route
    }
  }

  def processRequest(facadeRequest: FacadeRequest) = {
    val key = facadeRequest.clientCorrelationId.get
    val actorName = "Subscr-" + key
    context.child(actorName) match {
      case Some(actor) ⇒ actor.forward(facadeRequest)
      case None ⇒ context.actorOf(FeedSubscriptionActor.props(self, hyperbus, subscriptionManager), actorName) ! facadeRequest
    }
  }

  def isPingRequest(uri: String, method: String): Boolean = {
    uri == "/meta/ping" && method == "ping"
  }

  def pong(facadeRequest: FacadeRequest) = {
    val headers = Map(
      FacadeHeaders.CLIENT_CORRELATION_ID → Seq(facadeRequest.clientCorrelationId.get),
      FacadeHeaders.CLIENT_MESSAGE_ID → Seq(IdGenerator.create())
    )
    send(FacadeResponse(eu.inn.hyperbus.model.Status.OK, headers, Text("pong")))
  }

  def send(message: FacadeMessage): Unit = {
    if (isConnectionTerminated) {
      log.warning(s"Can't send message $message to $serverConnection/$remoteAddress: connection was terminated")
    }
    else {
      try {
        send(message.toFrame)
      } catch {
        case t: Throwable ⇒
          log.error(t, s"Can't serialize $message to $serverConnection/$remoteAddress")
      }
    }
  }
}

object WsRestWorker {
  def props(serverConnection: ActorRef,
            workerRoutes: WsRestRoutes,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager,
            clientAddress: String)
           (implicit inj: Injector) = Props(new WsRestWorker(
    serverConnection,
    workerRoutes,
    hyperbus,
    subscriptionManager,
    clientAddress))
}
