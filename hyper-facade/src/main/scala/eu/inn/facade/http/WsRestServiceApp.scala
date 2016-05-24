package eu.inn.facade.http

import akka.actor._
import akka.io.{IO, Inet, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.facade.metrics.MetricKeys
import eu.inn.hyperbus.Hyperbus
import scaldi.{Injectable, Injector}
import spray.can.Http
import spray.can.server.{ServerSettings, UHttp}
import spray.io.ServerSSLEngineProvider
import spray.routing._

import scala.collection.immutable
import scala.concurrent.Future

class WsRestServiceApp(implicit inj: Injector)
  extends RestServiceApp
  with Injectable {

  private val trackActiveConnections = metrics.counter(MetricKeys.ACTIVE_CONNECTIONS)
  private val rejectedConnectionsMeter = metrics.meter(MetricKeys.REJECTED_CONNECTS)

  val hyperbus = inject [Hyperbus]
  val subscriptionsManager = inject [SubscriptionsManager]

  override def startServer(interface: String,
                           port: Int,
                           serviceActorName: String,
                           backlog: Int,
                           options: immutable.Traversable[Inet.SocketOption],
                           settings: Option[ServerSettings])(route: ⇒ Route)(implicit system: ActorSystem, sslEngineProvider: ServerSSLEngineProvider,
                                                                             bindingTimeout: Timeout): Future[Http.Bound] = {

    val maxConnectionCount = config.getInt(s"${FacadeConfigPaths.HTTP}.max-connections")

    val serviceActor = system.actorOf(
      props = Props {
        new Actor {
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
                rejectedConnectionsMeter.mark()
              }
              else {
                val serverConnection = sender()
                connectionId += 1
                connectionCount += 1
                val worker = context.actorOf(
                  WsRestWorker.props(serverConnection,
                    new WsRestRoutes(route),
                    hyperbus,
                    subscriptionsManager,
                    remoteAddress.getAddress.toString
                  ), "wrkr-" + connectionId.toHexString
                )
                context.watch(worker)
                trackActiveConnections.inc()
              }
            case Terminated(worker) ⇒
              connectionCount -= 1
              trackActiveConnections.dec()
          }
        }
      },
      name = serviceActorName)

    val io = IO(UHttp)(system)
    io.ask(Http.Bind(serviceActor, interface, port, backlog, options, settings))(bindingTimeout).flatMap {
      case b: Http.Bound ⇒ Future.successful(b)
      case Tcp.CommandFailed(b: Http.Bind) ⇒
        // TODO: replace by actual exception when Akka #3861 is fixed.
        //       see https://github.com/akka/akka/issues/13861
        Future.failed(new RuntimeException(
          "Binding failed. Switch on DEBUG-level logging for `akka.io.TcpListener` to log the cause."))
    }(system.dispatcher)
  }
}
