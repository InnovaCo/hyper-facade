package eu.inn.facade.filter.http

import akka.actor.ActorSystem
import akka.io.IO
import eu.inn.facade.filter.RequestMapper._
import eu.inn.hyperbus.model.DynamicRequest
import spray.can.server.UHttp
import spray.can.{Http, websocket}
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.TextFrame
import spray.http.HttpRequest

case class Connect()

abstract class WsTestClient(connect: Http.Connect, val upgradeRequest: HttpRequest) extends WebSocketClientWorker {
  implicit val system = ActorSystem()

  override def receive = {
    case message: Connect ⇒
      context.become(handshaking orElse closeLogic)
      IO(UHttp) ! connect
  }

  def businessLogic: Receive = {
    case x @ websocket.UpgradedToWebSocket ⇒ onUpgrade

    case frame: TextFrame ⇒
      onMessage(frame)

    case dynamicRequest: DynamicRequest ⇒
      toTextFrame(dynamicRequest) foreach { frame ⇒
        connection ! frame
      }

    case _: Http.ConnectionClosed ⇒
      context.stop(self)
  }

  def onMessage(frame: TextFrame): Unit
  def onUpgrade: Unit
}
