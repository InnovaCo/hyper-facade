package eu.inn.facade.http

import spray.routing._

class WsRestRoutes(aroute: ⇒ Route) {
  def route: Route = aroute
}
