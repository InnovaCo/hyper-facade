package eu.inn.facade.filter.http

import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.Frame
import spray.http.HttpRequest


abstract class WsTestClient(connect: Http.Connect, val upgradeRequest: HttpRequest) extends WebSocketClientWorker {

  def businessLogic: Receive = {
    case frame: Frame =>
      onMessage(frame)

    case _: Http.ConnectionClosed =>
      context.stop(self)
  }

  def initConnection = IO(UHttp) ! connect

  def onMessage(frame: Frame)
}
