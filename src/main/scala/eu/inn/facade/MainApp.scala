package eu.inn.facade

import eu.inn.facade.workers.{HttpWorker, WsRestServiceApp}
import eu.inn.facade.modules.Injectors
import eu.inn.servicecontrol.api.Service
import scaldi.Injectable

object MainApp extends App with Injectable {

  implicit val injector = Injectors()
  val httpWorker = inject [HttpWorker]

  inject[Service].asInstanceOf[WsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }
}
