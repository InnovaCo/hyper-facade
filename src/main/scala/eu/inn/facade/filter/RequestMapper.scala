package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.binders.json._
import eu.inn.facade.filter.model.Headers
import eu.inn.facade.filter.model.DynamicRequestHeaders._
import eu.inn.hyperbus.model.{DynamicBody, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import spray.http
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._

object RequestMapper {

  def toDynamicRequest(headers: Headers, body: DynamicBody): DynamicRequest = {
    DynamicRequest(extractDynamicRequestHeader(headers), body)
  }

  def toHttpResponse(headers: Headers, body: DynamicBody): HttpResponse = {
    val statusCode = StatusCode.int2StatusCode(headers.statusCode getOrElse 200)
    val httpContentType: ContentType = contentType(body.contentType)
    val jsonBody = body.content.toJson
    HttpResponse(statusCode, HttpEntity(httpContentType, jsonBody), extractHttpHeaders(headers))
  }

  def toFailedHttpResponse(statusCode: Int, message: String): HttpResponse = {
    val httpStatusCode = StatusCode.int2StatusCode(statusCode)
    val httpContentType: ContentType = contentType(None)
    val jsonBody = Text(message).toJson
    HttpResponse(httpStatusCode, HttpEntity(httpContentType, jsonBody), List())
  }

  def toDynamicRequest(httpRequest: HttpRequest): DynamicRequest = {
    DynamicRequest(httpRequest.entity.data.toByteString.iterator.asInputStream)
  }

  private def contentType(contentType: Option[String]): ContentType = {
    contentType match {
      case None ⇒ `application/json`
      case Some(dynamicContentType) ⇒
        val mediaType = MediaTypes.register(MediaType.custom(dynamicContentType, null, true, false, Seq("json")))
        http.ContentType(mediaType, Some(`UTF-8`))
    }
  }

  private def extractHttpHeaders(headers: Headers): List[HttpHeader]= {
    var httpHeaders = List[HttpHeader]()
    headers.headers.foreach { header ⇒
      val (name, value) = header
      if (!isDynamicHeader(name)) httpHeaders = httpHeaders :+ RawHeader(name, value)
    }
    httpHeaders
  }

  private def extractDynamicRequestHeader(headers: Headers): RequestHeader = {
    var contentTypeOption: Option[String] = None
    val contentType = headers → CONTENT_TYPE
    if (contentType != null) contentTypeOption = Some(contentType)

    var correlationIdOption: Option[String] = None
    val correlationId = headers → CORRELATION_ID
    if (correlationId != null) correlationIdOption = Some(correlationId)
    RequestHeader(headers → URL, headers → METHOD, contentTypeOption, headers → MESSAGE_ID, correlationIdOption)
  }
}
