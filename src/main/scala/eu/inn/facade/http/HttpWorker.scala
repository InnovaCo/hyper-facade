package eu.inn.facade.http

import akka.actor.ActorSystem
import eu.inn.binders.json._
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model.{DynamicRequestHeaders, TransitionalHeaders}
import eu.inn.facade.raml.RamlConfig
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
    val resourceUri = ramlConfig.resourceUri(uri)
    val responsePromise = Promise[HttpResponse]()
    filterIn(request, resourceUri.pattern.specific) map {
      case (filteredHeaders, filteredBody) ⇒
        if (filteredHeaders.hasStatusCode) responsePromise.complete(Success(RequestMapper.toHttpResponse(filteredHeaders, filteredBody)))
        else {
          val filteredRequest = RequestMapper.toDynamicRequest(filteredHeaders, filteredBody)
          val responseFuture = hyperBus <~ filteredRequest flatMap {
            case response: Response[DynamicBody] ⇒ filterOut(response, resourceUri.pattern.specific, request.method.name)
          } map {
            case (headers: TransitionalHeaders, body: DynamicBody) ⇒
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

  def filterIn(request: HttpRequest, uri: String): Future[(TransitionalHeaders, DynamicBody)] = {
    val dynamicRequest = RequestMapper.toDynamicRequest(request, uri)
    val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
    val contentType = headers.singleValueHeader(DynamicRequestHeaders.CONTENT_TYPE)
    filterChainComposer.inputFilterChain(uri, request.method.name, contentType).applyFilters(headers, dynamicBody)
  }

  def filterOut(response: Response[DynamicBody], uriPattern: String, method: String): Future[(TransitionalHeaders, DynamicBody)] = {
    val statusCode = response.status
    val body = response.body
    filterChainComposer.outputFilterChain(uriPattern, method).applyFilters(TransitionalHeaders(api.uri.Uri(uriPattern), response.headers, Some(statusCode)), body)
  }

  private def exceptionToHttpResponse(t: Throwable): HttpResponse = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    HttpResponse(StatusCodes.InternalServerError, t.toString + " #" + errorId)
  }
}

