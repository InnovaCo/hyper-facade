package eu.inn.facade.http

import akka.actor._
import eu.inn.binders.dynamic.Text
import eu.inn.facade.events.{ReliableFeedSubscriptionActor, SubscriptionsManager, UnreliableFeedSubscriptionActor}
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard.Ok
import eu.inn.hyperbus.serialization.RequestHeader
import scaldi.{Injectable, Injector}
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.Frame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class WsRestWorker(val serverConnection: ActorRef,
                   workerRoutes: WsRestRoutes,
                   hyperBus: HyperBus,
                   subscriptionManager: SubscriptionsManager,
                   clientAddress: String)
                  (implicit inj: Injector)
  extends HttpServiceActor
  with websocket.WebSocketServerWorker
  with ActorLogging
  with Injectable {

  var isConnectionTerminated = false
  var remoteAddress = clientAddress
  var request: Option[HttpRequest] = None

  val filterChainComposer = inject[FilterChainFactory]
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
          request = Some(wsContext.request)

          // todo: this is dangerous, network infrastructure should guarantee that http_x_forwarded_for is always overridden at server-side
          remoteAddress = wsContext.request.headers.find(_.is("http_x_forwarded_for")).map(_.value).getOrElse(clientAddress)
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
        val dynamicRequest = RequestMapper.toDynamicRequest(message)
        val url = dynamicRequest.url
        val method = dynamicRequest.method
        if (isPingRequest(url, method)) pong(dynamicRequest)
        else {
          val requestWithClientIp = RequestMapper.addField("http_x_forwarded_for", remoteAddress, dynamicRequest)
          processRequest(requestWithClientIp)
        }
      }
      catch {
        case t: Throwable ⇒
          val msg = message.payload.utf8String
          //          val msgShort = msg.substring(0, Math.min(msg.length, 240))
          log.warning(s"Can't deserialize websocket message '$msg' from ${sender()}/$remoteAddress. $t")
          None
      }

    case x: FrameCommandFailed =>
      log.error(s"Frame command $x failed from ${sender()}/$remoteAddress")

    case response: Response[DynamicBody] ⇒
      send(response)

    case dynamicRequest: DynamicRequest ⇒
      send(dynamicRequest)
  }

  def httpRequests: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      workerRoutes.route
    }
  }

  def processRequest(request: DynamicRequest) = request match {
    case request @ DynamicRequest(RequestHeader(url, _, _, messageId, correlationId), _) ⇒
      val key = correlationId.getOrElse(messageId)
      val actorName = "Subscr-" + key
      context.child(actorName) match {
        case Some(actor) ⇒ actor.forward(request)
        case None ⇒ context.actorOf(feedSubscriptionActorProps(url), actorName) ! request
      }
  }

  def feedSubscriptionActorProps(url: String): Props = {
    if (ramlConfig.isReliableEventFeed(url)) ReliableFeedSubscriptionActor.props(self, hyperBus, subscriptionManager)
    else if (ramlConfig.isUnreliableEventFeed(url)) UnreliableFeedSubscriptionActor.props(self, hyperBus, subscriptionManager)
    else throw new IllegalArgumentException("This resource doesn't contain an event feed")
  }

  def isPingRequest(url: String, method: String): Boolean = {
    url == "/meta/ping" && method == "ping"
  }

  def pong(dynamicRequest: DynamicRequest) = request match {
    case request @ DynamicRequest(RequestHeader(_, _, _, messageId, correlationId), _) ⇒
      val finalCorrelationId = correlationId.getOrElse(messageId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(finalCorrelationId)
      send(Ok(DynamicBody(Text("pong"))))
  }

  def send(message: Message[Body]): Unit = {
    if (isConnectionTerminated) {
      log.warning(s"Can't send message $message to $serverConnection/$remoteAddress: connection was terminated")
    }
    else {
      try {
        send(RequestMapper.toFrame(message))
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
            hyperBus: HyperBus,
            subscriptionManager: SubscriptionsManager,
            clientAddress: String)
           (implicit inj: Injector) = Props(new WsRestWorker(
    serverConnection,
    workerRoutes,
    hyperBus,
    subscriptionManager,
    clientAddress))
}
