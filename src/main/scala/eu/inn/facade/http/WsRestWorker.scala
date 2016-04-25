package eu.inn.facade.http

import akka.actor._
import eu.inn.binders.value.Text
import eu.inn.facade.events.{FeedSubscriptionActor, SubscriptionsManager}
import eu.inn.facade.metrics.MetricKeys
import eu.inn.facade.model._
import eu.inn.hyperbus.{Hyperbus, IdGenerator}
import eu.inn.metrics.Metrics
import scaldi.{Injectable, Injector}
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.Frame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.util.control.NonFatal

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

  val metrics = inject[Metrics]
  val trackWsTimeToLive = metrics.timer(MetricKeys.WS_LIFE_TIME).time()
  val trackWsMessages = metrics.meter(MetricKeys.WS_MESSAGE_COUNT)
  val trackHeartbeat = metrics.meter(MetricKeys.HEARTBEAT)
  var isConnectionTerminated = false
  var remoteAddress = clientAddress
  var httpRequest: Option[HttpRequest] = None

  override def preStart(): Unit = {
    super.preStart()
    serverConnection ! Http.Register(context.self)
    context.watch(serverConnection)
    if (log.isDebugEnabled) {
      log.debug(s"New connection with $serverConnection/$remoteAddress")
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    trackWsTimeToLive.stop()
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
        trackWsMessages.mark()
        trackHeartbeat.mark()
        val originalRequest = FacadeRequest(message)
        val uriPattern = originalRequest.uri.pattern.specific
        val uri = spray.http.Uri()
        if (uri.scheme.nonEmpty || uri.authority.nonEmpty) {
          throw new IllegalArgumentException(s"Uri $uri has invalid format. Only path and query is allowed.")
        }
        val method = originalRequest.method
        if (isPingRequest(uriPattern, method)) {
          pong(originalRequest)
        }
        else {
          httpRequest match {
            case Some(h) ⇒
              val requestContext = FacadeRequestContext.create(remoteAddress, h, originalRequest)
              processRequest(requestContext, originalRequest.copy(headers = requestContext.requestHeaders))

            case None ⇒
              throw new RuntimeException(s"httpRequest is empty while processing frame.")
          }
        }
      }
      catch {
        case NonFatal(t) ⇒
          // todo: send error response to the client
          val msg = message.payload.utf8String
          //          val msgShort = msg.substring(0, Math.min(msg.length, 240))
          log.warning(s"Can't deserialize WS message '$msg' from ${sender()}/$remoteAddress. $t")
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

  def processRequest(requestContext: FacadeRequestContext, facadeRequest: FacadeRequest) = {
    val key = facadeRequest.clientCorrelationId.get
    val actorName = "Subscr-" + key
    val requestWithContext = ContextWithRequest(requestContext, facadeRequest)
    context.child(actorName) match {
      case Some(actor) ⇒ actor.forward(requestWithContext)
      case None ⇒ context.actorOf(FeedSubscriptionActor.props(self, hyperbus, subscriptionManager), actorName) ! requestWithContext
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
