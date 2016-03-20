package eu.inn.facade

import com.typesafe.config.Config
import eu.inn.facade.http.{HttpWorker, WsRestServiceApp}
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.Hyperbus
import org.slf4j.LoggerFactory
import scaldi.Injectable

object MainApp extends App with Injectable {

  initProperties()

  implicit val injector = Injectors()
  val httpWorker = inject [HttpWorker]
  val log = LoggerFactory.getLogger(MainApp.getClass.getName)
  val config = inject[Config]

  new WsRestServiceApp(config.getString("inn.facade.rest-api.host"), config.getInt("inn.facade.rest-api.port")) {
    start {
      httpWorker.restRoutes.routes
    }
  }
  val hyperbus = inject [Hyperbus]  // it's time to initialize hyperbus
  log.info("hyperbus is starting...: {}", hyperbus)

  def initProperties(): Unit = {
    if (args.length > 0) {
      val ramlFileRelativePath = args(0)
      System.setProperty(ConfigsFactory.RAML_CONFIG_RELATIVE_PATH, ramlFileRelativePath)
    }
  }
}
