package eu.inn.facade.perf

import com.typesafe.config.Config
import eu.inn.facade.http.{HttpWorker, WsRestServiceApp}
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.Hyperbus
import scaldi.Injectable

object FacadeApp extends App with Injectable {

  implicit val injector = Injectors()
  val httpWorker = inject [HttpWorker]
  val config = inject[Config]
  val host = config.getString("perf-test.host")
  val port = config.getInt("perf-test.port")

  new WsRestServiceApp(host, port) {
    start {
      httpWorker.restRoutes.routes
    }
  }
  val hyperbus = inject [Hyperbus]  // it's time to initialize hyperbus
}
