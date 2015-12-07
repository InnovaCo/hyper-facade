package eu.inn.facade.filter.http

import akka.actor.ActorRef
import eu.inn.facade.filter.RequestMapper._
import eu.inn.facade.filter.chain.{FilterChain, FilterChainComponent}
import eu.inn.hyperbus.model.DynamicRequest
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.util.{Failure, Success}

abstract class WsTestWorker(val filterChain: FilterChain) extends HttpServiceActor with websocket.WebSocketServerWorker with FilterChainComponent {
  import scala.concurrent.ExecutionContext.Implicits.global
  private var _serverConnection: ActorRef = _

  def serverConnection = _serverConnection

  override def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      _serverConnection = sender()
      context.become(handshaking orElse closeLogic)
      serverConnection ! Http.Register(context.self)
  }

  def businessLogic: Receive = {
    case frame: TextFrame =>
      val dynamicRequest @ DynamicRequest(requestHeader, dynamicBody) = toDynamicRequest(frame)
      val headers = extractHeaders(requestHeader)
      filterChain.applyInputFilters(headers, dynamicBody) map {
        case Success((headers, body)) ⇒ exposeDynamicRequest(toDynamicRequest(headers, body))
        case Failure(ex) ⇒ println(ex)
      }

    case request: DynamicRequest =>
      toTextFrame(request) map { frame => send(frame) }

    case request: HttpRequest =>
      val dynamicRequest = toDynamicRequest(request)
      exposeHttpRequest(request)
  }

  def exposeDynamicRequest(dynamicRequest: DynamicRequest): Unit = ???
  def exposeHttpRequest(request: HttpRequest): Unit = ???
}