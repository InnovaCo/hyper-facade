package eu.inn.facade.workers

import spray.routing._

class WsRestRoutes(aroute: ⇒ Route) {
  def route: Route = aroute
}
