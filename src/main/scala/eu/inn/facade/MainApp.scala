package eu.inn.facade

import eu.inn.facade.http.{HttpWorker, WsRestServiceApp}
import eu.inn.facade.modules.Injectors
import eu.inn.servicecontrol.api.Service
import org.slf4j.LoggerFactory
import scaldi.Injectable

object MainApp extends App with Injectable {

  initProperties()

  implicit val injector = Injectors()
  val httpWorker = inject [HttpWorker]
  val log = LoggerFactory.getLogger(MainApp.getClass.getName)

  inject[Service].asInstanceOf[WsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }

  def initProperties(): Unit = {
    if (args.length > 0) {
      val ramlFileRelativePath = args(0)
      System.setProperty(ConfigsFactory.RAML_CONFIG_RELATIVE_PATH, ramlFileRelativePath)
    }
  }
}
