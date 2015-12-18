package eu.inn.facade.http

import akka.actor._
import eu.inn.binders.dynamic.Text
import eu.inn.facade.events.{SubscriptionActor, SubscriptionsManager}
import eu.inn.facade.filter.chain.FilterChainComponent
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import scaldi.{Injectable, Injector}
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.Frame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.{HttpServiceActor, Route}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class WsRestWorker(val serverConnection: ActorRef,
                   workerRoutes: WsRestRoutes,
                   hyperBus: HyperBus,
                   subscriptionManager: SubscriptionsManager,
                   clientAddress: String)
                  (implicit inj: Injector) extends
  HttpServiceActor
  with websocket.WebSocketServerWorker
  with ActorLogging
  with Injectable {

  var isConnectionTerminated = false
  var remoteAddress = clientAddress
  var request: Option[HttpRequest] = None

  val filterChainComposer = inject[FilterChainComponent]
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
    case handshakeRequest @ websocket.HandshakeRequest(state) ⇒ {
      state match {
        case wsContext: websocket.HandshakeContext ⇒
          request = Some(wsContext.request)

          // todo: this is dangerous, network infrastructure should guarantee that http_x_forwarded_for is always overridden at server-side
          remoteAddress = wsContext.request.headers.find(_.is("http_x_forwarded_for")).map(_.value).getOrElse(clientAddress)
        case _ ⇒
      }
      handshaking(handshakeRequest)
    }

    case ev: Http.ConnectionClosed ⇒
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
      try {
        val dynamicRequest = RequestMapper.toDynamicRequest(message)
        val url = dynamicRequest.url
        val method = dynamicRequest.method
        if (ramlConfig.isPingRequest(url)) pong(dynamicRequest)
        else {
          val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
          filterChainComposer.inputFilterChain(url, method).applyFilters(headers, dynamicBody) map {
            case Success((filteredHeaders, filteredBody)) ⇒
              val filteredDynamicRequest = RequestMapper.toDynamicRequest(filteredHeaders, filteredBody)
              processRequest(filteredDynamicRequest)

            case Failure(ex) ⇒
              val msg = message.payload.utf8String
              log.error(ex, s"Failed to apply input filter chain for request $msg")
          }
        }
      }
      catch {
        case t: Throwable ⇒
          val msg = message.payload.utf8String
          val msgShort = msg.substring(0, Math.min(msg.length, 240))
          log.warning(s"Can't deserialize websocket message '$msg' from ${sender()}/$remoteAddress. $t")
          None
      }

    case x: FrameCommandFailed =>
      log.error(s"Frame command $x failed from ${sender()}/$remoteAddress")

    case dynamicRequest: DynamicRequest ⇒
        val url = dynamicRequest.url
        val method = dynamicRequest.method
        val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
      filterChainComposer.outputFilterChain(url, method).applyFilters(headers, dynamicBody) map {
          case Success((filteredHeaders, filteredBody)) ⇒
            send(RequestMapper.toDynamicRequest(filteredHeaders, filteredBody))

          case Failure(ex) ⇒
            val msg = dynamicRequest.toString
            log.error(ex, s"Failed to apply output filter chain for response $msg")
        }
  }

  def httpRequests: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      workerRoutes.route
    }
  }

  def processRequest(request: DynamicRequest) = request match {
    case request @ DynamicRequest(RequestHeader(_,_,_,messageId,correlationId), _) ⇒
      val key = correlationId.getOrElse(messageId)
      val actorName = "Subscr-" + key
      context.child(actorName) match {
        case Some(actor) ⇒ actor.forward(request)
        case None ⇒ context.actorOf(Props(classOf[SubscriptionActor], self, hyperBus, subscriptionManager), actorName) ! request
      }
  }

  def pong(dynamicRequest: DynamicRequest) = request match {
    case request @ DynamicRequest(RequestHeader(_, _, _, messageId, correlationId), _) ⇒ {
      val finalCorrelationId = correlationId.getOrElse(messageId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(finalCorrelationId)
      send(Ok(DynamicBody(Text("pong"))))
    }
  }

  def send(message: Message[Body]): Unit = {
    if (isConnectionTerminated) {
      log.warning(s"Can't send message $message to $serverConnection/$remoteAddress: connection was terminated")
    }
    else {
        try {
          send(RequestMapper.toFrame(message))
        }
        catch {
          case t: Throwable ⇒
            log.error(t, s"Can't serialize $message to $serverConnection/$remoteAddress")
        }
    }
  }
}

class WsRestRoutes(aroute: ⇒ Route) {
  def route: Route = aroute
}
