package eu.inn.facade.filter.http

import akka.actor.{Actor, ActorRef}
import eu.inn.facade.filter.RequestMapper._
import eu.inn.facade.filter.chain.{FilterChain, FilterChainComponent}
import eu.inn.hyperbus.model.{DynamicBody, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.util.Success

class WsTestServer(listener: ActorRef) extends Actor {

  def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      val serverConnection = sender()
      serverConnection ! Http.Register(listener)
  }
}

abstract class WsTestWorker(val serverConnection: ActorRef, val filterChain: FilterChain, onConnected: () ⇒ _) extends HttpServiceActor with websocket.WebSocketServerWorker with FilterChainComponent {
  import scala.concurrent.ExecutionContext.Implicits.global
  
  override def receive = {
    onConnected()
    handshaking orElse closeLogic
  }

  def businessLogic: Receive = {
    case frame: TextFrame =>
      val dynamicRequest = toDynamicRequest(frame)
      val headers = extractHeaders(dynamicRequest)
      var dynamicBody: DynamicBody = null
      dynamicRequest match {
        case DynamicRequest(_, body) ⇒ dynamicBody = body
      }
      filterChain.applyInputFilters(headers, dynamicBody) map {
        case Success((headers, body)) ⇒ exposeDynamicRequest(extractDynamicHeader(headers), body)
      }
      
    case request: DynamicRequest => toTextFrame(request) map { frame => send(frame) }

    case request: HttpRequest =>
      val dynamicRequest = toDynamicRequest(request)
      exposeHttpRequest(request)
  }

  def exposeDynamicRequest(header: RequestHeader, dynamicBody: DynamicBody): Unit = ???
  def exposeHttpRequest(request: HttpRequest): Unit = ???
}