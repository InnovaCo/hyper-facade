package eu.inn.facade.http

import akka.actor.ActorSystem
import eu.inn.binders.dynamic.Null
import eu.inn.binders.json._
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model.{DynamicRequestHeaders, Headers}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model.standard.{DynamicGet, EmptyBody}
import eu.inn.hyperbus.model.{DynamicBody, Response}
import eu.inn.hyperbus.transport.api
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}
import spray.http.ContentTypes._
import spray.http._
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

class HttpWorker(implicit inj: Injector) extends Injectable {

  val hyperBus = inject[HyperBus]
  val ramlConfig = inject[RamlConfig]
  val filterChainComposer = inject[FilterChainFactory]
  val log = LoggerFactory.getLogger(HttpWorker.this.getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  val restRoutes = new RestRoutes {
    val anyMethod = get | put | post | head | patch | delete | options
    val request = extract(_.request)

    val routes: Route =
      anyMethod {
        (request & requestUri) { (request, uri) ⇒
          onSuccess(processRequest(request, uri.path.toString)) { response ⇒
            complete(response)
          }
        }
      }
  }

  def processRequest(request: HttpRequest, uri: String): Future[HttpResponse] = {
    val resourceUriPattern = ramlConfig.resourceStateUri(uri)
    val responsePromise = Promise[HttpResponse]()
    filterIn(request, resourceUriPattern) map {
      case (headers, body) ⇒
        if (headers.hasStatusCode) responsePromise.complete(Success(RequestMapper.toHttpResponse(headers, body)))
        else {
          val resourceUri = api.uri.Uri(resourceUriPattern)
          val responseFuture = hyperBus <~ DynamicGet(resourceUri, DynamicBody(EmptyBody.contentType, Null)) flatMap {
            case response: Response[DynamicBody] ⇒ filterOut(response, resourceUriPattern, request.method.name)
          } map {
            case (headers: Headers, body: DynamicBody) ⇒
              val intStatusCode = headers.statusCode.getOrElse(200)
              val statusCode = StatusCode.int2StatusCode(intStatusCode)
              HttpResponse(statusCode, HttpEntity(`application/json`, body.content.toJson))
          } recover {
            case t: Throwable ⇒
              exceptionToHttpResponse(t)
          }
          responsePromise.completeWith(responseFuture)
        }
    }
    responsePromise.future
  }

  def filterIn(request: HttpRequest, uri: String): Future[(Headers, DynamicBody)] = {
    val dynamicRequest = RequestMapper.toDynamicRequest(request)
    val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
    val contentType = headers.singleValueHeader(DynamicRequestHeaders.CONTENT_TYPE)
    filterChainComposer.inputFilterChain(uri, request.method.name, contentType).applyFilters(headers, dynamicBody)
  }

  def filterOut(response: Response[DynamicBody], uriPattern: String, method: String): Future[(Headers, DynamicBody)] = {
    val statusCode = response.status
    val body = response.body
    filterChainComposer.outputFilterChain(uriPattern, method).applyFilters(Headers(api.uri.Uri(uriPattern), response.headers, Some(statusCode)), body)
  }

  private def exceptionToHttpResponse(t: Throwable): HttpResponse = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    HttpResponse(StatusCodes.InternalServerError, t.toString + " #" + errorId)
  }
}

