package eu.inn.facade.http

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

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
    if (httpRequest.entity.isEmpty) DynamicRequest(RequestHeader(null, null, None, null, None, null), DynamicBody(Obj(Map())))
    else DynamicRequest(new ByteArrayInputStream(httpRequest.entity.data.toByteArray))
  }

  def toDynamicResponse(headers: Headers, dynamicBody: DynamicBody): Response[Body] = {
    val messageId = headers.headers(MESSAGE_ID).head
    val correlationId = headers.headers(CORRELATION_ID).head
    headers.statusCode match {
      case Some(200) ⇒ Ok(dynamicBody, headers.headers, messageId, correlationId)
      case Some(201) ⇒ Created(DynamicCreatedBody(dynamicBody.content), headers.headers, messageId, correlationId)
      case Some(202) ⇒ Accepted(dynamicBody, headers.headers, messageId, correlationId)
      case Some(203) ⇒ NonAuthoritativeInformation(dynamicBody, headers.headers, messageId, correlationId)
      case Some(204) ⇒ NoContent(dynamicBody, headers.headers, messageId, correlationId)
      case Some(205) ⇒ ResetContent(dynamicBody, headers.headers, messageId, correlationId)
      case Some(206) ⇒ PartialContent(dynamicBody, headers.headers, messageId, correlationId)
      case Some(207) ⇒ MultiStatus(dynamicBody, headers.headers, messageId, correlationId)

      case Some(300) ⇒ MultipleChoices(dynamicBody, headers.headers, messageId, correlationId)
      case Some(301) ⇒ MovedPermanently(dynamicBody, headers.headers, messageId, correlationId)
      case Some(302) ⇒ Found(dynamicBody, headers.headers, messageId, correlationId)
      case Some(303) ⇒ SeeOther(dynamicBody, headers.headers, messageId, correlationId)
      case Some(304) ⇒ NotModified(dynamicBody, headers.headers, messageId, correlationId)
      case Some(305) ⇒ UseProxy(dynamicBody, headers.headers, messageId, correlationId)
      case Some(307) ⇒ TemporaryRedirect(dynamicBody, headers.headers, messageId, correlationId)

      case Some(400) ⇒ BadRequest(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(401) ⇒ Unauthorized(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(402) ⇒ PaymentRequired(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(403) ⇒ Forbidden(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(404) ⇒ NotFound(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(405) ⇒ MethodNotAllowed(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(406) ⇒ NotAcceptable(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(407) ⇒ ProxyAuthenticationRequired(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(408) ⇒ RequestTimeout(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(409) ⇒ Conflict(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(410) ⇒ Gone(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(411) ⇒ LengthRequired(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(412) ⇒ PreconditionFailed(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(413) ⇒ RequestEntityTooLarge(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(414) ⇒ RequestUriTooLong(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(415) ⇒ UnsupportedMediaType(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(416) ⇒ RequestedRangeNotSatisfiable(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(417) ⇒ ExpectationFailed(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(422) ⇒ UnprocessableEntity(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(423) ⇒ Locked(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(424) ⇒ FailedDependency(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(429) ⇒ TooManyRequest(errorBody(dynamicBody), headers.headers, messageId, correlationId)

      case Some(500) ⇒ eu.inn.hyperbus.model.standard.InternalServerError(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(501) ⇒ NotImplemented(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(502) ⇒ BadGateway(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(503) ⇒ ServiceUnavailable(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(504) ⇒ GatewayTimeout(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(505) ⇒ HttpVersionNotSupported(errorBody(dynamicBody), headers.headers, messageId, correlationId)
      case Some(507) ⇒ InsufficientStorage(errorBody(dynamicBody), headers.headers, messageId, correlationId)
    }
  }

  def unfold(dynamicRequest: DynamicRequest): (Headers, DynamicBody) = {
    dynamicRequest match {
      case DynamicRequest(requestHeader, dynamicBody) ⇒
        val headers = extractRequestHeaders(requestHeader)
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

  def extractRequestHeaders(dynamicHeader: RequestHeader): Headers = {
    var headers = Map[String, Seq[String]]()
    headers += (METHOD → Seq(dynamicHeader.method))
    dynamicHeader.contentType match {
      case Some(contentType) ⇒ headers += (CONTENT_TYPE → Seq(contentType))
      case None ⇒
    }
    headers += (MESSAGE_ID → Seq(dynamicHeader.messageId))
    dynamicHeader.correlationId match {
      case Some(correlationId) ⇒ headers += (CORRELATION_ID → Seq(correlationId))
      case None ⇒
    }
    Headers(dynamicHeader.uri, headers, None)
  }

  def extractResponseHeaders(statusCode: Int, headers: Map[String, Seq[String]], messageId: String, correlationId: String): Headers = {
    val responseHeaders = headers + (MESSAGE_ID → Seq(messageId), CORRELATION_ID → Seq(correlationId))
    Headers(null, responseHeaders, Some(statusCode))
  }

  def extractDynamicHeader(headers: Headers): RequestHeader = {
    val method = headers.singleValueHeader(METHOD).getOrElse(Method.GET)
    val contentType = headers.singleValueHeader(CONTENT_TYPE)
    val messageId = headers.singleValueHeader(MESSAGE_ID).get
    val correlationId = headers.singleValueHeader(CORRELATION_ID)
    val otherHeaders = headers.headers - (METHOD, CONTENT_TYPE, MESSAGE_ID, CORRELATION_ID)
    RequestHeader(headers.uri, method, contentType, messageId, correlationId, otherHeaders)
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
        if (!isDynamicHeader(name)) httpHeaders = httpHeaders :+ RawHeader(name, value.head)
    }
    httpHeaders
  }
}
