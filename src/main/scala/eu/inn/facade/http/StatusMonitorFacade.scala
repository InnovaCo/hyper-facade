package eu.inn.facade.http

import akka.actor.ActorSystem
import eu.inn.binders.dynamic.Null
import eu.inn.binders.json._
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.standard.{DynamicGet, EmptyBody, ErrorResponse}
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}
import spray.http.ContentTypes._
import spray.http._
import spray.routing._
import spray.routing.directives.MethodDirectives._
import spray.routing.directives.RouteDirectives._

import scala.concurrent.ExecutionContext

class StatusMonitorFacade(implicit inj: Injector) extends Injectable {

  val hyperBus = inject [HyperBus]
  val log = LoggerFactory.getLogger(StatusMonitorFacade.this.getClass.getName)
  implicit val actorSystem = inject [ActorSystem]
  implicit val executionContext = inject [ExecutionContext]

  val statusMonitorRoutes = new RestRoutes {

    val routes: Route =
      get {
        complete {
          hyperBus <~ DynamicGet("/test-service", DynamicBody(EmptyBody.contentType, Null)) map { result ⇒
            HttpResponse(StatusCodes.OK, HttpEntity(`application/json`, result.body.content.toJson))
          } recover {
            case er: ErrorResponse ⇒
              HttpResponse(er.status, HttpEntity(`application/json`, er.body.toJson))

            case t: Throwable ⇒
              exceptionToHttpResponse(t)
          }
        }
      }
  }

  private def exceptionToHttpResponse(t: Throwable): HttpResponse = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    HttpResponse(StatusCodes.InternalServerError, t.toString + " #"+errorId)
  }
}

