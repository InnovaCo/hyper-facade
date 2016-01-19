package eu.inn.facade.http

import akka.actor.ActorSystem
import akka.io.IO
import eu.inn.facade.http.RequestMapper._
import eu.inn.hyperbus.model.DynamicRequest
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.HttpRequest

import scala.collection.mutable

case class Connect()

abstract class WsTestClient(connect: Http.Connect, val upgradeRequest: HttpRequest) extends WebSocketClientWorker {
  implicit val system = ActorSystem()
  val messageQueue: mutable.Queue[TextFrame] = mutable.Queue()

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
      connection ! toFrame(dynamicRequest)

    case _: Http.ConnectionClosed ⇒
      context.stop(self)
  }

  def onMessage(frame: TextFrame): Unit
  def onUpgrade: Unit
}