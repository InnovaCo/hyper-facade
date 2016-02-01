package eu.inn.facade.http

import java.io.ByteArrayOutputStream

import akka.util.ByteString
import eu.inn.binders.dynamic.{Null, Obj, Text}
import eu.inn.binders.json._
import eu.inn.facade.filter.model.DynamicRequestHeaders._
import eu.inn.facade.filter.model.Headers
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._

object RequestMapper {

  def toDynamicRequest(headers: Headers, body: DynamicBody): DynamicRequest = {
    DynamicRequest(extractDynamicHeader(headers), body)
  }

  def toDynamicRequest(frame: Frame): DynamicRequest = {
    DynamicRequest(frame.payload.iterator.asInputStream)
  }

  def toDynamicRequest(httpRequest: HttpRequest): DynamicRequest = {
    if (httpRequest.entity.isEmpty) DynamicRequest(RequestHeader(null, null, None, null, None), DynamicBody(Obj(Map())))
    else DynamicRequest(httpRequest.entity.data.toByteString.iterator.asInputStream)
  }

  def toDynamicResponse(headers: Headers, dynamicBody: DynamicBody): Response[Body] = {
    headers.statusCode match {
      case Some(200) ⇒ Ok(dynamicBody)
      case Some(201) ⇒ Created(DynamicCreatedBody(dynamicBody.content))
      case Some(202) ⇒ Accepted(dynamicBody)
      case Some(203) ⇒ NonAuthoritativeInformation(dynamicBody)
      case Some(204) ⇒ NoContent(dynamicBody)
      case Some(205) ⇒ ResetContent(dynamicBody)
      case Some(206) ⇒ PartialContent(dynamicBody)
      case Some(207) ⇒ MultiStatus(dynamicBody)

      case Some(300) ⇒ MultipleChoices(dynamicBody)
      case Some(301) ⇒ MovedPermanently(dynamicBody)
      case Some(302) ⇒ Found(dynamicBody)
      case Some(303) ⇒ SeeOther(dynamicBody)
      case Some(304) ⇒ NotModified(dynamicBody)
      case Some(305) ⇒ UseProxy(dynamicBody)
      case Some(307) ⇒ TemporaryRedirect(dynamicBody)

      case Some(400) ⇒ BadRequest(errorBody(dynamicBody))
      case Some(401) ⇒ Unauthorized(errorBody(dynamicBody))
      case Some(402) ⇒ PaymentRequired(errorBody(dynamicBody))
      case Some(403) ⇒ Forbidden(errorBody(dynamicBody))
      case Some(404) ⇒ NotFound(errorBody(dynamicBody))
      case Some(405) ⇒ MethodNotAllowed(errorBody(dynamicBody))
      case Some(406) ⇒ NotAcceptable(errorBody(dynamicBody))
      case Some(407) ⇒ ProxyAuthenticationRequired(errorBody(dynamicBody))
      case Some(408) ⇒ RequestTimeout(errorBody(dynamicBody))
      case Some(409) ⇒ Conflict(errorBody(dynamicBody))
      case Some(410) ⇒ Gone(errorBody(dynamicBody))
      case Some(411) ⇒ LengthRequired(errorBody(dynamicBody))
      case Some(412) ⇒ PreconditionFailed(errorBody(dynamicBody))
      case Some(413) ⇒ RequestEntityTooLarge(errorBody(dynamicBody))
      case Some(414) ⇒ RequestUriTooLong(errorBody(dynamicBody))
      case Some(415) ⇒ UnsupportedMediaType(errorBody(dynamicBody))
      case Some(416) ⇒ RequestedRangeNotSatisfiable(errorBody(dynamicBody))
      case Some(417) ⇒ ExpectationFailed(errorBody(dynamicBody))
      case Some(422) ⇒ UnprocessableEntity(errorBody(dynamicBody))
      case Some(423) ⇒ Locked(errorBody(dynamicBody))
      case Some(424) ⇒ FailedDependency(errorBody(dynamicBody))
      case Some(429) ⇒ TooManyRequest(errorBody(dynamicBody))

      case Some(500) ⇒ eu.inn.hyperbus.model.standard.InternalServerError(errorBody(dynamicBody))
      case Some(501) ⇒ NotImplemented(errorBody(dynamicBody))
      case Some(502) ⇒ BadGateway(errorBody(dynamicBody))
      case Some(503) ⇒ ServiceUnavailable(errorBody(dynamicBody))
      case Some(504) ⇒ GatewayTimeout(errorBody(dynamicBody))
      case Some(505) ⇒ HttpVersionNotSupported(errorBody(dynamicBody))
      case Some(507) ⇒ InsufficientStorage(errorBody(dynamicBody))
    }
  }

  def unfold(dynamicRequest: DynamicRequest): (Headers, DynamicBody) = {
    dynamicRequest match {
      case DynamicRequest(requestHeader, dynamicBody) ⇒
        val headers = extractHeaders(requestHeader)
        (headers, dynamicBody)
    }
  }
  
  def toFrame(message: Message[Body]): Frame = {
    val ba = new ByteArrayOutputStream()
    message.serialize(ba)
    TextFrame(ByteString(ba.toByteArray))
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

  def extractHeaders(dynamicHeader: RequestHeader): Headers = {
    var headers = Map[String, String]()
    headers += (URL → dynamicHeader.url)
    headers += (METHOD → dynamicHeader.method)
    dynamicHeader.contentType match {
      case Some(contentType) ⇒ headers += (CONTENT_TYPE → contentType)
      case None ⇒
    }
    headers += (MESSAGE_ID → dynamicHeader.messageId)
    dynamicHeader.correlationId match {
      case Some(correlationId) ⇒ headers += (CORRELATION_ID → correlationId)
      case None ⇒
    }
    Headers(headers, None)
  }

  def extractDynamicHeader(headers: Headers): RequestHeader = {
    val headersMap = headers.headers
    RequestHeader(headersMap(URL), headersMap(METHOD), headersMap.get(CONTENT_TYPE), headersMap(MESSAGE_ID), headersMap.get(CORRELATION_ID))
  }

  def addField(fieldName: String, fieldValue: String, dynamicRequest: DynamicRequest): DynamicRequest = {
    dynamicRequest match {
      case DynamicRequest(requestHeader, dynamicBody) ⇒
        val fieldMap = dynamicBody.content.asMap
        val updatedBody = DynamicBody(Obj(fieldMap + (fieldName → Text(fieldValue))))
        DynamicRequest(requestHeader, updatedBody)
    }
  }

  private def errorBody(dynamicBody: DynamicBody): ErrorBody = {
    val dynamicFields = dynamicBody.content.asMap
    val code = dynamicFields("code").asString
    val description: Option[String] = dynamicFields.get("description") match {
      case Some(value) ⇒ Some(value.asString)
      case None ⇒ None
    }
    val error = dynamicFields.get("errorId")
    val extra = dynamicFields.getOrElse("extra", Null)
    val contentType = dynamicBody.contentType
    error match {
      case Some(errorId) ⇒ ErrorBody(code, description, errorId.asString, extra, contentType)
      case None ⇒ ErrorBody(code = code, description = description, extra = extra, contentType = contentType)
    }
  }

  private def contentType(contentType: Option[String]): spray.http.ContentType = {
    contentType match {
      case None ⇒ `application/json`
      case Some(dynamicContentType) ⇒
        val mediaType = MediaTypes.register(MediaType.custom(dynamicContentType, null, true, false, Seq("json")))
        spray.http.ContentType(mediaType, Some(`UTF-8`))
    }
  }

  private def extractHttpHeaders(headers: Headers): List[HttpHeader]= {
    var httpHeaders = List[HttpHeader]()
    headers.headers.foreach {
      case (name, value) ⇒
        if (!isDynamicHeader(name)) httpHeaders = httpHeaders :+ RawHeader(name, value)
    }
    httpHeaders
  }
}
