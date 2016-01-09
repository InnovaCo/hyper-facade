package eu.inn.facade.http

import akka.actor._
import akka.io.{IO, Inet, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.facade.StatsReporterFactory
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.hyperbus.HyperBus
import scaldi.{Injectable, Injector}
import spray.can.Http
import spray.can.server.{ServerSettings, UHttp}
import spray.io.ServerSSLEngineProvider
import spray.routing._

import scala.collection.immutable
import scala.concurrent.Future

class WsRestServiceApp(interface: String, port: Int)(implicit inj: Injector)
  extends RestServiceApp(interface, port)
  with Injectable {

  private val stats = inject[StatsReporterFactory].createStats("http")
  private val connectionCountStat = stats.counter("connection-count")
  private val rejectedConnectionsMetter = stats.meter("rejected-connects")
  val config = inject[Config]
  val hyperBus = inject[HyperBus]
  val subscriptionsManager = inject[SubscriptionsManager]

  @volatile private[this] var _refFactory: Option[ActorRefFactory] = None

  override implicit def actorRefFactory = _refFactory getOrElse sys.error(
    "Route creation is not fully supported before `startServer` has been called, " +
      "maybe you can turn your route definition into a `def` ?")

  override def startServer(interface: String,
                           port: Int,
                           serviceActorName: String,
                           backlog: Int,
                           options: immutable.Traversable[Inet.SocketOption],
                           settings: Option[ServerSettings])(route: ⇒ Route)(implicit system: ActorSystem, sslEngineProvider: ServerSSLEngineProvider,
                                                                             bindingTimeout: Timeout): Future[Http.Bound] = {

    val maxConnectionCount = config.getInt("inn.facade.http.max-connections")

    val serviceActor = system.actorOf(
      props = Props {
        new Actor {
          _refFactory = Some(context)
          val noMoreConnectionsWorker = context.actorOf(
            Props(classOf[NoMoreConnectionsWorker], maxConnectionCount),
            "no-more-connections"
          )
          var connectionId: Long = 0
          var connectionCount: Long = 0

          def receive = {
            case Http.Connected(remoteAddress, localAddress) =>
              if (connectionCount >= maxConnectionCount) {
                sender() ! Http.Register(noMoreConnectionsWorker)
                rejectedConnectionsMetter.mark()
              }
              else {
                val serverConnection = sender()
                connectionId += 1
                connectionCount += 1
                val worker = context.actorOf(
                  WsRestWorker.props(serverConnection, new WsRestRoutes(route), hyperBus, subscriptionsManager, remoteAddress.toString),
                  "wrkr-" + connectionId.toHexString
                )
                context.watch(worker)
                connectionCountStat.inc()
              }
            case Terminated(worker) ⇒
              connectionCount -= 1
              connectionCountStat.dec()
          }
        }
      },
      name = serviceActorName)

    //val system = 0
    val io = IO(UHttp)(system)
    io.ask(Http.Bind(serviceActor, interface, port, backlog, options, settings))(bindingTimeout).flatMap {
      case b: Http.Bound ⇒ Future.successful(b)
      case Tcp.CommandFailed(b: Http.Bind) ⇒
        // TODO: replace by actual exception when Akka #3861 is fixed.
        //       see https://www.assembla.com/spaces/akka/tickets/3861
        Future.failed(new RuntimeException(
          "Binding failed. Switch on DEBUG-level logging for `akka.io.TcpListener` to log the cause."))
    }(system.dispatcher)
  }
}
