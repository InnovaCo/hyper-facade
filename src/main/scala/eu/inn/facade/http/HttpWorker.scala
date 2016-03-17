package eu.inn.facade.http

import akka.actor.ActorSystem
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.{FacadeRequest, FacadeResponse, FilterInterruptException}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}
import spray.http._
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class HttpWorker(implicit inj: Injector) extends Injectable {

  val hyperBus = inject[HyperBus]
  val ramlConfig = inject[RamlConfig]
  val filterChain = inject[FilterChain]
  val log = LoggerFactory.getLogger(HttpWorker.this.getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  val restRoutes = new RestRoutes {
    val request = extract(_.request)
    val routes: Route = request { (request) ⇒
        onSuccess(processRequest(request)) { response ⇒
          complete(response)
        }
      }
  }

  def processRequest(request: HttpRequest): Future[HttpResponse] = {
    val resourceUri = ramlConfig.resourceUri(request.uri.path.toString)
    val facadeRequest = FacadeRequest(resourceUri, request)

    filterChain.filterRequest(facadeRequest) flatMap { filteredRequest ⇒
      hyperBus <~ filteredRequest.toDynamicRequest flatMap { response ⇒
        filterChain.filterResponse(facadeRequest, FacadeResponse(response))
      }
    } recover {
      case e: FilterInterruptException ⇒
        if (e.getCause != null) {
          log.error(s"Request execution interrupted: $request", e)
        }
        e.response

      case NonFatal(e) ⇒
        val response = RequestMapper.exceptionToResponse(e)
        log.error(s"Service answered with error #${response.body.asInstanceOf[ErrorBody].errorId}. Stopping actor")
        FacadeResponse(response)
    } map { response ⇒
      response.toHttpResponse
    }
  }

/*
    val body = request.method match {
      case HttpMethods.GET ⇒
        QueryBody.fromQueryString(request.uri.query.toMap)
      case HttpMethods.DELETE ⇒
        EmptyBody
      case _ ⇒
        (StringDeserializer.dynamicBody(Some(request.entity.asString)),
          updateRequestContentType(
            TransitionalHeaders(uri, Headers(Header.METHOD → Seq(request.method.toString.toLowerCase)), None)
          ))
    }


    val responsePromise = Promise[HttpResponse]()
    filterIn(request, resourceUri) map {
      case (filteredHeaders, filteredBody) ⇒
        if (filteredHeaders.hasStatusCode) responsePromise.complete(Success(RequestMapper.toHttpResponse(filteredHeaders, filteredBody)))
        else {
          val filteredRequest = RequestMapper.toDynamicRequest(filteredHeaders, filteredBody)
          val responseFuture = hyperBus <~ filteredRequest flatMap {
            case response: Response[DynamicBody] ⇒ filterOut(response, resourceUri, request.method.name)
          } map {
            case (headers: TransitionalHeaders, body: DynamicBody) ⇒
              RequestMapper.toHttpResponse(headers, body)
          } recover {
            case t: Throwable ⇒ RequestMapper.exceptionToHttpResponse(t)
          }
          responsePromise.completeWith(responseFuture)
        }
    }
    responsePromise.future

  }

  def filterIn(request: HttpRequest, uri: api.uri.Uri): Future[(TransitionalHeaders, DynamicBody)] = {
    val (body, headers) = request.method match {
      case HttpMethods.GET ⇒
        (QueryBody.fromQueryString(request.uri.query.toMap),
          TransitionalHeaders(uri, Headers(Header.METHOD → Seq(Method.GET)), None))
      case HttpMethods.DELETE ⇒
        (EmptyBody,
          TransitionalHeaders(uri, Headers(Header.METHOD → Seq(Method.DELETE)), None))
      case _ ⇒
        (StringDeserializer.dynamicBody(Some(request.entity.asString)),
          updateRequestContentType(
            TransitionalHeaders(uri, Headers(Header.METHOD → Seq(request.method.toString.toLowerCase)), None)
          ))
    }

    val contentType = headers.headerOption(CONTENT_TYPE)
    filterChainComposer.requestFilterChain(uri, request.method.name, contentType).applyFilters(headers, body)
  }

  def filterOut(response: Response[DynamicBody], uri: api.uri.Uri, method: String): Future[(TransitionalHeaders, DynamicBody)] = {
    val body = response.body
    val headers = updateResponseContentType(response, uri)
    filterChainComposer.responseFilterChain(uri, method).applyFilters(headers, body)
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

  def updateResponseContentType(response: Response[DynamicBody], uri: api.uri.Uri): TransitionalHeaders = {
    val newContentType = response.headerOption(CONTENT_TYPE) match {
      case Some(contentType) ⇒ CERTAIN_CONTENT_TYPE_START + contentType + CERTAIN_CONTENT_TYPE_END
      case None ⇒ COMMON_CONTENT_TYPE
    }
    val headers = response.headers + (CONTENT_TYPE → Seq(newContentType))
    TransitionalHeaders(uri, headers, Some(response.status))
  }
  */
}

