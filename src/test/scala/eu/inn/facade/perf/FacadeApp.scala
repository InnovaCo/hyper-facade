package eu.inn.facade.perf

import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.workers.{HttpWorker, WsRestServiceApp}
import eu.inn.facade.modules.Injectors
import eu.inn.servicecontrol.api.Service
import scaldi.Injectable

object FacadeApp extends App with Injectable {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/perf-test.raml")
  implicit val injector = Injectors()
  val httpWorker = inject [HttpWorker]

  inject[Service].asInstanceOf[WsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }
}
