package eu.inn.facade

import eu.inn.facade.modules.TestInjectors
import eu.inn.facade.workers.{HttpWorker, TestWsRestServiceApp}
import eu.inn.servicecontrol.api.Service
import scaldi.Injectable

object FacadeApp extends App with Injectable {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/perf-test.raml")
  implicit val injector = TestInjectors()
  val httpWorker = inject [HttpWorker]

  inject[Service].asInstanceOf[TestWsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }
}
