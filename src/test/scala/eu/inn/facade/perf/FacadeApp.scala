package eu.inn.facade.perf

import com.typesafe.config.Config
import eu.inn.facade.http.{HttpWorker, WsRestServiceApp}
import eu.inn.facade.modules.{ConfigModule, FiltersModule, ServiceModule}
import eu.inn.hyperbus.HyperBus
import scaldi.{Injectable, Injector}

object FacadeApp extends App with Injectable {

  implicit val injector = getInjector
  val statusMonitorFacade = inject [HttpWorker]
  val config = inject[Config]
  val host = config.getString("perf-test.host")
  val port = config.getInt("perf-test.port")

  new WsRestServiceApp(host, port) {
    start {
      pathPrefix("test-service") {
        statusMonitorFacade.statusMonitorRoutes.routes
      }
    }
  }
  val hyperBus = inject [HyperBus]  // it's time to initialize hyperbus

  def getInjector: Injector = {
    val filtersModule = new FiltersModule
    val injector = new ConfigModule :: filtersModule :: new ServiceModule
    filtersModule.initOuterBindings
    injector.initNonLazy
  }
}
