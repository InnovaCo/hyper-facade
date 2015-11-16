package eu.inn.facade

import akka.actor._
import akka.event.LoggingReceive
import akka.io.{IO, Inet, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.util.ConfigComponent
import eu.inn.util.akka.ActorSystemComponent
import eu.inn.util.http.RestServiceComponent
import eu.inn.util.metrics.StatsComponent
import spray.can.Http
import spray.can.server.{ServerSettings, UHttp}
import spray.io.ServerSSLEngineProvider
import spray.routing._

import scala.collection.immutable
import scala.concurrent.Future

trait WsRestServiceComponent extends RestServiceComponent {
  this: ActorSystemComponent
    with StatsComponent
    with SubscriptionsManager
    with HyperBusComponent
    with ConfigComponent ⇒

  class WebsocketsRestServiceApp(interface: String, port: Int)
    extends RestServiceApp(interface, port) {

    @volatile private[this] var _refFactory: Option[ActorRefFactory] = None

    override implicit def actorRefFactory = _refFactory getOrElse sys.error(
      "Route creation is not fully supported before `startServer` has been called, " +
        "maybe you can turn your route definition into a `def` ?")

    override def startServer(interface: String = this.interface,
                    port: Int = this.port,
                    serviceActorName: String = "test-facade-actor",
                    backlog: Int,
                    options: immutable.Traversable[Inet.SocketOption],
                    settings: Option[ServerSettings])(route: ⇒ Route)(implicit system: ActorSystem, sslEngineProvider: ServerSSLEngineProvider,
                                                                             bindingTimeout: Timeout): Future[Http.Bound] = {
      val serviceActor = system.actorOf(
        props = Props {
          new Actor {
            _refFactory = Some(context)
            def receive = LoggingReceive {
              case Http.Connected(remoteAddress, localAddress) =>
                val serverConnection = sender()
                val conn = context.actorOf(
                  Props(classOf[WsRestWorker], serverConnection, new WsRestRoutes(route), hyperBus, WsRestServiceComponent.this)
                )
                serverConnection ! Http.Register(conn)
                val childCount = context.children.size
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
}



