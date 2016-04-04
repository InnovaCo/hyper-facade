package eu.inn.facade.http

import akka.actor.ActorRef
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.FacadeRequest
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.routing.HttpServiceActor

abstract class WsTestWorker(filterChain: FilterChain) extends HttpServiceActor with websocket.WebSocketServerWorker with MockContext {
  import context._
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
      val facadeRequest = FacadeRequest(frame)
      val context = mockContext(facadeRequest)
      filterChain.filterRequest(context, facadeRequest) map { filteredRequest ⇒
        exposeFacadeRequest(filteredRequest)
      }
  }

  def exposeFacadeRequest(facadeRequest: FacadeRequest): Unit
}