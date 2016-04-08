package eu.inn.facade.http

import akka.actor.ActorSystem
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import org.slf4j.LoggerFactory
import scaldi.Injector
import spray.http._
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.{ExecutionContext, Future}

class HttpWorker(implicit val injector: Injector) extends RequestProcessor {
  val log = LoggerFactory.getLogger(getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  val restRoutes = new RestRoutes {
    val request = extract(_.request)
    val routes: Route =
      request { (request) ⇒
        clientIP { ip =>
          complete(processRequest(request, ip.toString))
        }
      }
  }

  def processRequest(request: HttpRequest, remoteAddress: String): Future[HttpResponse] = {
    val facadeRequest = FacadeRequest(request)
    val requestContext = FacadeRequestContext.create(
        remoteAddress,
        request,
        facadeRequest,
        ramlConfig.rewriteIndex
      )
    processRequestToFacade(requestContext, facadeRequest) map { response ⇒
      response.toHttpResponse
    }
  }
}

