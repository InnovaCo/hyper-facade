package eu.inn.facade.workers

import akka.actor.{ActorLogging, ActorRefFactory}
import eu.inn.facade.model.FacadeHeaders
import spray.http.ContentTypes._
import spray.http.{HttpEntity, HttpResponse}
import spray.routing._
import spray.routing.directives.{HeaderDirectives, RouteDirectives}

class NoMoreConnectionsWorker(maxConnectionCount: Int) extends HttpServiceActor with ActorLogging {
  def receive: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      HeaderDirectives.optionalHeaderValueByName(FacadeHeaders.CLIENT_IP) { forwardedFor ⇒
        RouteDirectives.complete {
          log.warning(s"Maximum ($maxConnectionCount) active input connection count is exceed for ${forwardedFor.getOrElse("'address is unknown'")}.")
          HttpResponse(503, HttpEntity(`text/plain`, "Connection/worker limit is exceeded"))
        }
      }
    }
  }
}
