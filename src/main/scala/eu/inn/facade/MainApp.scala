package eu.inn.facade

import akka.actor.ActorSystem
import eu.inn.facade.injectors.{FiltersModule, ConfigModule}

object MainApp extends App with ComponentRegistry {

  implicit override lazy val actorSystem = ActorSystem("eu-inn", config)
  implicit val injector = new ConfigModule :: new FiltersModule

  new WebsocketsRestServiceApp("localhost", 8080) {
    start {
      path("test-facade") {
        statusMonitorRoutes.routes
      }
    }
  }
  val hb = hyperBus // reference lazy val to start up
  log.info("hyperbus is starting...: {}", hb)
}
