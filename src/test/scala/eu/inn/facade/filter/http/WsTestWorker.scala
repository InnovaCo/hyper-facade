package eu.inn.facade.filter.http

import akka.actor.ActorRef
import eu.inn.facade.filter.RequestMapper._
import eu.inn.facade.filter.chain.{FilterChain, FilterChainComponent}
import eu.inn.hyperbus.model.DynamicRequest
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.util.Success

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
      toDynamicRequest(frame) match {
        case DynamicRequest(requestHeader, dynamicBody) ⇒
          val headers = extractHeaders(requestHeader)
          filterChain.applyInputFilters(headers, dynamicBody) map {
            case Success((headers, body)) ⇒ exposeDynamicRequest(toDynamicRequest(headers, body))
          }
      }

    case request: DynamicRequest =>
      request match {
        case DynamicRequest(requestHeader, dynamicBody) ⇒
          val headers = extractHeaders(requestHeader)
          filterChain.applyOutputFilters(headers, dynamicBody) map {
            case Success((headers, body)) ⇒
              send(toFrame(toDynamicRequest(headers, body)))
          }
      }

    case request: HttpRequest =>
  }

  def exposeDynamicRequest(dynamicRequest: DynamicRequest): Unit = ???

  def exposeHttpRequest(request: HttpRequest): Unit = ???
}