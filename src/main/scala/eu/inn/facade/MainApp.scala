package eu.inn.facade

import eu.inn.facade.http.{StatusMonitorFacade, WsRestServiceApp}
import eu.inn.facade.modules.{ConfigModule, FiltersModule, ServiceModule}
import eu.inn.hyperbus.HyperBus
import org.slf4j.LoggerFactory
import scaldi.{Injector, Injectable}

object MainApp extends App with Injectable {

  implicit val injector = getInjector
  val statusMonitorFacade = inject[StatusMonitorFacade]
  val log = LoggerFactory.getLogger(MainApp.getClass.getName)

  new WsRestServiceApp("localhost", 8080) {
    start {
      path("/test-service") {
        statusMonitorFacade.statusMonitorRoutes.routes
      }
    }
  }
  val hyperBus = inject[HyperBus]  // it's time to initialize hyperbus
  log.info("hyperbus is starting...: {}", hyperBus)

  def getInjector: Injector = {
    val filtersModule = new FiltersModule
    val injector = new ConfigModule :: filtersModule :: new ServiceModule
    filtersModule.initOuterBindings
    injector
  }
}
