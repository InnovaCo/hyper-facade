package eu.inn.facade.http

import akka.actor.ActorRef
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.http.RequestMapper._
import eu.inn.hyperbus.model.DynamicRequest
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.util.Success

abstract class WsTestWorker(val inputFilterChain: FilterChain, val outputFilterChain: FilterChain) extends HttpServiceActor with websocket.WebSocketServerWorker {

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
        case DynamicRequest(uri, dynamicBody, requestHeaders) ⇒
          val headers = extractRequestHeaders(uri, requestHeaders)
          inputFilterChain.applyFilters(headers, dynamicBody) onComplete {
            case Success((filteredHeaders, body)) ⇒ exposeDynamicRequest(toDynamicRequest(filteredHeaders, body))
          }
      }

    case request: DynamicRequest =>
      request match {
        case DynamicRequest(uri, dynamicBody, requestHeader) ⇒
          val headers = extractRequestHeaders(uri, requestHeader)
          outputFilterChain.applyFilters(headers, dynamicBody) onComplete {
            case Success((filteredHeaders, body)) ⇒
              send(toFrame(toDynamicRequest(filteredHeaders, body)))
          }
      }

    case request: HttpRequest =>
  }

  def exposeDynamicRequest(dynamicRequest: DynamicRequest): Unit = ???

  def exposeHttpRequest(request: HttpRequest): Unit = ???
}