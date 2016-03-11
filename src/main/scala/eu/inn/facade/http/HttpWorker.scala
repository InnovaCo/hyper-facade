package eu.inn.facade.http

import akka.actor.ActorSystem
import eu.inn.binders.json._
import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model.TransitionalHeaders
import eu.inn.facade.filter.model.Headers._
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.{DynamicBody, Response}
import eu.inn.hyperbus.model.Header._
import eu.inn.hyperbus.transport.api
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
            case t: Throwable ⇒ RequestMapper.exceptionToHttpResponse(t)
          }
          responsePromise.completeWith(responseFuture)
        }
    }
    responsePromise.future

  }

  def filterIn(request: HttpRequest, uri: String): Future[(TransitionalHeaders, DynamicBody)] = {
    val dynamicRequest = RequestMapper.toDynamicRequest(request, uri)
    val (headers, dynamicBody) = RequestMapper.unfold(dynamicRequest)
    val updatedHeaders = request.method.name match {
      case method if (Seq("post", "put", "patch").contains(method)) ⇒ updateRequestContentType(headers)
      case _ ⇒ headers
    }
    val contentType = updatedHeaders.headerOption(CONTENT_TYPE)
    filterChainComposer.inputFilterChain(uri, request.method.name, contentType).applyFilters(updatedHeaders, dynamicBody)
  }

  def filterOut(response: Response[DynamicBody], uriPattern: String, method: String): Future[(TransitionalHeaders, DynamicBody)] = {
    val body = response.body
    val headers = updateResponseContentType(response, uriPattern)
    filterChainComposer.outputFilterChain(uriPattern, method).applyFilters(headers, body)
  }

  def updateRequestContentType(headers: TransitionalHeaders): TransitionalHeaders = {
    val newContentType = headers.headerOption(CONTENT_TYPE) match {
      case contentType @ (Some(COMMON_CONTENT_TYPE) | None) ⇒ None
      case contentType @ Some(value) if (value.startsWith(CERTAIN_CONTENT_TYPE_START) && value.endsWith(CERTAIN_CONTENT_TYPE_END)) ⇒
        val beginIndex = CERTAIN_CONTENT_TYPE_START.size
        val endIndex = value.size - CERTAIN_CONTENT_TYPE_END.size
        Some(value.substring(beginIndex, endIndex))
      case _ ⇒ None
    }
    val headersMap = newContentType match {
      case Some(contentType) ⇒ headers.headers + (CONTENT_TYPE → Seq(contentType))
      case None ⇒ headers.headers - CONTENT_TYPE
    }
    TransitionalHeaders(headers.uri, headersMap, headers.statusCode)
  }

  def updateResponseContentType(response: Response[DynamicBody], uriPattern: String): TransitionalHeaders = {
    val newContentType = response.headerOption(CONTENT_TYPE) match {
      case Some(contentType) ⇒ CERTAIN_CONTENT_TYPE_START + contentType + CERTAIN_CONTENT_TYPE_END
      case None ⇒ COMMON_CONTENT_TYPE
    }
    val headers = response.headers + (CONTENT_TYPE → Seq(newContentType))
    TransitionalHeaders(api.uri.Uri(uriPattern), headers, Some(response.status))
  }
}

