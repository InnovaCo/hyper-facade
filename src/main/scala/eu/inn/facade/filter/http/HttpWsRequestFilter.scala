package eu.inn.facade.filter.http

import eu.inn.facade.model._
import eu.inn.hyperbus.model.Header

import scala.concurrent.{ExecutionContext, Future}

class HttpWsRequestFilter extends RequestFilter {

  override def apply(context: RequestFilterContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val headersBuilder = Map.newBuilder[String, Seq[String]]

      request.headers.foreach {
        case (Header.CONTENT_TYPE, value :: tail)
          if value.startsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_START)
            && value.endsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_END) ⇒

          val beginIndex = FacadeHeaders.CERTAIN_CONTENT_TYPE_START.length
          val endIndex = value.length - FacadeHeaders.CERTAIN_CONTENT_TYPE_END.length

          headersBuilder += Header.CONTENT_TYPE → Seq(value.substring(beginIndex, endIndex))

        case (k, v) if HttpWsRequestFilter.directFacadeToHyperBus.contains(k) ⇒
          headersBuilder += HttpWsRequestFilter.directFacadeToHyperBus(k) → v
      }

      request.copy(
        headers = headersBuilder.result()
      )
    }
  }
}

object HttpWsRequestFilter {
  val directFacadeToHyperBus =  FacadeHeaders.directHeaderMapping.toMap
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