package eu.inn.facade.filter.http

import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.TextFrame
import spray.http.HttpRequest
import akka.actor.ActorSystem

abstract class WsTestClient(connect: Http.Connect, val upgradeRequest: HttpRequest) extends WebSocketClientWorker {
  implicit val system = ActorSystem()

  def businessLogic: Receive = {
    case frame: TextFrame =>
      onMessage(frame)

    case _: Http.ConnectionClosed =>
      context.stop(self)
  }

  def initConnection = IO(UHttp) ! connect

  def onMessage(frame: TextFrame)
}
