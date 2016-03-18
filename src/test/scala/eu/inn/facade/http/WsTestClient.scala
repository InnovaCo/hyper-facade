package eu.inn.facade.http

import akka.actor.ActorSystem
import akka.io.{IO, Tcp}
import eu.inn.facade.model.{FacadeMessage, FacadeRequest}
import eu.inn.hyperbus.model.DynamicRequest
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.HttpRequest

case class Connect()
case class Disconnect()

class WsTestClient(connect: Http.Connect, val upgradeRequest: HttpRequest)(implicit actorSystem: ActorSystem) extends WebSocketClientWorker {

  override def receive = {
    case message: Connect ⇒
      context.become(handshaking orElse closeLogic)
      IO(UHttp) ! connect
  }

  def businessLogic: Receive = {
    case x @ websocket.UpgradedToWebSocket ⇒ onUpgrade()

    case frame: TextFrame ⇒
      onMessage(frame)

    case facadeMessage: FacadeMessage ⇒
      connection ! facadeMessage.toFrame

    case _: Http.ConnectionClosed ⇒
      context.stop(self)

    case _: Disconnect ⇒
      connection ! Http.Close
      context.stop(self)
  }

  def onMessage(frame: TextFrame): Unit = ()
  def onUpgrade(): Unit = ()
}
