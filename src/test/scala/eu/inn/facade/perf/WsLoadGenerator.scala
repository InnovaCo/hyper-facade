package eu.inn.facade.perf

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorRef, ActorSystem, Props}
import eu.inn.binders.dynamic.Null
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.http.{Connect, Disconnect, WsTestClient}
import eu.inn.facade.model.{FacadeHeaders, FacadeRequest}
import eu.inn.hyperbus.model.Header
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.can.Http
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object WsLoadGenerator extends App {

  implicit val actorSystem = ActorSystem()

  val config = new ConfigsFactory().config.getConfig("perf-test")
  val host = config.getString("host")
  val port = config.getInt("port")
  val uriPattern = config.getString("ws.endpoint")
  val connect = Http.Connect(host, port)
  val onUpgradeGetReq = HttpRequest(HttpMethods.GET, uriPattern, upgradeHeaders(host, port))
  val initialClientsCount = config.getInt("ws.loader-count")
  val connectionFailureRate = config.getDouble("ws.connection-failure-rate")
  val loadIterationInterval = config.getInt("ws.load-iteration-interval-seconds") * 1000

  performSession()

  def performSession(): Unit = {
    var iterationNumber = 0
    var clients = createClients(initialClientsCount, iterationNumber)
    startLoad(clients)
    Thread.sleep(loadIterationInterval)
    val sessionLengthSeconds = config.getInt("ws.session-length-seconds")
    val startTime = System.currentTimeMillis()
    while (!sessionFinished(sessionLengthSeconds, startTime)) {
      iterationNumber += 1
      clients = failClients(clients)
      clients = restoreClients(clients, iterationNumber)
      Thread.sleep(loadIterationInterval)
    }

    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  def sessionFinished(sessionLengthSeconds: Int, startTime: Long): Boolean = {
    val duration = System.currentTimeMillis() - startTime
    val remaining = (sessionLengthSeconds * 1000) - duration
    println(s"Session duration $duration ms, remaining $remaining ms")
    (System.currentTimeMillis() - startTime) >= sessionLengthSeconds * 1000
  }

  def createClients(clientsCount: Int, iterationNumber: Int) = {
    var clients = Seq[ActorRef]()
    val connectedClients = new AtomicInteger(0)
    val maxPreviousActorId = iterationNumber * initialClientsCount
    for ( i ← 1 to clientsCount) {
      val newClientActorId = maxPreviousActorId + i
      clients =  clients :+ actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onUpgrade() = connectedClients.incrementAndGet()
      }), "WsLoader-" + newClientActorId)
    }
    connectClients(clients)
    while (connectedClients.get() < clients.size) {
      Thread.sleep(500)
    }
    clients
  }

  def startLoad(clients: Seq[ActorRef]): Unit = {
    clients foreach { client ⇒
      client ! FacadeRequest(Uri(uriPattern), "subscribe",
        Map(Header.CONTENT_TYPE → Seq("application/vnd+test-1.json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq(client.path.name),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq(client.path.name)),
        Null
      )
    }
  }

  def connectClients(clients: Seq[ActorRef]): Unit = {
    clients foreach {
      client ⇒ client ! Connect()
    }
  }

  def restoreClients(clients: Seq[ActorRef], iterationNumber: Int): Seq[ActorRef] = {
    val toBeRestored = initialClientsCount - failClientsCount
    val newClients = createClients(toBeRestored, iterationNumber)
    startLoad(newClients)
    clients ++ newClients
  }

  def failClients(clients: Seq[ActorRef]): Seq[ActorRef] = {
    var liveClients = clients
    for ( i ← 1 to failClientsCount) {
      liveClients.head ! Disconnect()
      liveClients = liveClients.tail
    }
    liveClients
  }

  def failClientsCount: Int = {
    (initialClientsCount * connectionFailureRate).toInt
  }

  def upgradeHeaders(host: String, port: Int) = List(
    HttpHeaders.Host(host, port),
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate"))
}
