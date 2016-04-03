package eu.inn.facade.http

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.io.IO
import eu.inn.facade.model.FacadeMessage
import org.scalatest.concurrent.ScalaFutures
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

import scala.concurrent.Promise
import scala.util.Success
import scala.util.control.NonFatal

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

trait WsTestClientHelper extends ScalaFutures {
  implicit def patience: PatienceConfig

  def createWsClient(name: String, uri: String,
                       onMessageString: String ⇒ Unit, host: String = "localhost", port: Int = 54321)
                      (implicit actorSystem: ActorSystem): ActorRef = {

    val connect = Http.Connect(host, port)
    val onUpgradeGetReq = HttpRequest(HttpMethods.GET, uri, upgradeHeaders(host, port))
    val onClientUpgradePromise = Promise[Boolean]()
    val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
      val lock = new Object
      override def onMessage(frame: TextFrame): Unit = {
        lock.synchronized {
          onMessageString(frame.payload.utf8String)
        }
      }
      override def onUpgrade(): Unit = {
        onClientUpgradePromise.complete(Success(true))
      }
    }), name)
    client ! Connect() // init websocket connection
    try {
      onClientUpgradePromise.future.futureValue
      client
    }
    catch {
      case NonFatal(e) ⇒
        actorSystem.stop(client)
        throw e
    }
  }

  def upgradeHeaders(host: String, port: Int) = List(
    HttpHeaders.Host(host, port),
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate")
  )
}