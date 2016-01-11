package eu.inn.facade

import eu.inn.binders.dynamic.Text
import eu.inn.facade.http.{StatusMonitorFacade, WsRestServiceApp}
import eu.inn.facade.modules.{ConfigModule, FiltersModule, ServiceModule}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.standard.Ok
import org.scalatest.{FreeSpec, Matchers}
import scaldi.{Injectable, Injector}

import scala.io.Source

class FacadeIntegrationTest extends FreeSpec with Matchers {

  "Facade integration" - {
    "simple http request" in {
      implicit val injector = getInjector
      val statusMonitorFacade = Injectable.inject[StatusMonitorFacade]

      new WsRestServiceApp("localhost", 8080) {
        start {
          pathPrefix("test-service") {
            statusMonitorFacade.statusMonitorRoutes.routes
          }
        }
      }
      val hyperBus = Injectable.inject[HyperBus]  // it's time to initialize hyperbus
      new TestServiceForFacade(hyperBus).onCommand(Ok(DynamicBody(Text("response"))))

      // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
      // bound to socket, so we need this stupid timeout to initialize the listener
      Thread.sleep(1000)

      Source.fromURL("http://localhost:8080/test-service", "UTF-8").mkString shouldBe """"response""""
    }
  }

  def getInjector: Injector = {
    val filtersModule = new FiltersModule
    val injector = new ConfigModule :: filtersModule :: new ServiceModule
    filtersModule.initOuterBindings
    injector.initNonLazy
    injector
  }
}
