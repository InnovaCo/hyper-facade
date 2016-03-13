package eu.inn.facade.http

import java.io.ByteArrayOutputStream

import akka.util.ByteString
import eu.inn.binders.dynamic.{Null, Obj, Text}
import eu.inn.binders.json._
import eu.inn.facade.filter.model.Headers._
import eu.inn.facade.filter.model.TransitionalHeaders
import eu.inn.hyperbus._
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.{NoTransportRouteException, uri}
import org.slf4j.LoggerFactory
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._

object RequestMapper {

  val log = LoggerFactory.getLogger(RequestMapper.getClass)

  def toDynamicRequest(headers: TransitionalHeaders, body: DynamicBody): DynamicRequest = {
    DynamicRequest(RequestHeader(headers.uri, headers.headers), body)
  }

  def toDynamicRequest(frame: Frame): DynamicRequest = {
    DynamicRequest(frame.payload.iterator.asInputStream)
  }
/*
  def toDynamicRequest(httpRequest: HttpRequest, uri: api.uri.Uri): DynamicRequest = {
    httpRequest.method.name.toLowerCase match {
      case Method.GET ⇒
        val header = RequestHeader(uri, Headers(Header.METHOD → Seq(httpRequest.method.name.toLowerCase)))
        val body = QueryBody.fromQueryString(httpRequest.uri.query.toMap)
        DynamicRequest(header, body)

        // todo: нельзя так сериализовать?
      case _ ⇒ DynamicRequest(new ByteArrayInputStream(httpRequest.entity.data.toByteArray))
    }
  }
*/

  def toDynamicResponse(headers: TransitionalHeaders, dynamicBody: DynamicBody): Response[Body] = {
    headers.statusCode match {
      case Some(200) ⇒ Ok(dynamicBody, Headers.plain(headers.headers))
      case Some(201) ⇒ Created(DynamicCreatedBody(dynamicBody.content), Headers.plain(headers.headers))
      case Some(202) ⇒ Accepted(dynamicBody, Headers.plain(headers.headers))
      case Some(203) ⇒ NonAuthoritativeInformation(dynamicBody, Headers.plain(headers.headers))
      case Some(204) ⇒ NoContent(dynamicBody, Headers.plain(headers.headers))
      case Some(205) ⇒ ResetContent(dynamicBody, Headers.plain(headers.headers))
      case Some(206) ⇒ PartialContent(dynamicBody, Headers.plain(headers.headers))
      case Some(207) ⇒ MultiStatus(dynamicBody, Headers.plain(headers.headers))

      case Some(300) ⇒ MultipleChoices(dynamicBody, Headers.plain(headers.headers))
      case Some(301) ⇒ MovedPermanently(dynamicBody, Headers.plain(headers.headers))
      case Some(302) ⇒ Found(dynamicBody, Headers.plain(headers.headers))
      case Some(303) ⇒ SeeOther(dynamicBody, Headers.plain(headers.headers))
      case Some(304) ⇒ NotModified(dynamicBody, Headers.plain(headers.headers))
      case Some(305) ⇒ UseProxy(dynamicBody, Headers.plain(headers.headers))
      case Some(307) ⇒ TemporaryRedirect(dynamicBody, Headers.plain(headers.headers))

      case Some(400) ⇒ BadRequest(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(401) ⇒ Unauthorized(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(402) ⇒ PaymentRequired(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(403) ⇒ Forbidden(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(404) ⇒ NotFound(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(405) ⇒ MethodNotAllowed(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(406) ⇒ NotAcceptable(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(407) ⇒ ProxyAuthenticationRequired(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(408) ⇒ RequestTimeout(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(409) ⇒ Conflict(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(410) ⇒ Gone(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(411) ⇒ LengthRequired(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(412) ⇒ PreconditionFailed(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(413) ⇒ RequestEntityTooLarge(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(414) ⇒ RequestUriTooLong(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(415) ⇒ UnsupportedMediaType(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(416) ⇒ RequestedRangeNotSatisfiable(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(417) ⇒ ExpectationFailed(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(422) ⇒ UnprocessableEntity(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(423) ⇒ Locked(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(424) ⇒ FailedDependency(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(429) ⇒ TooManyRequest(errorBody(dynamicBody), Headers.plain(headers.headers))

      case Some(500) ⇒ model.InternalServerError(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(501) ⇒ NotImplemented(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(502) ⇒ BadGateway(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(503) ⇒ ServiceUnavailable(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(504) ⇒ GatewayTimeout(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(505) ⇒ HttpVersionNotSupported(errorBody(dynamicBody), Headers.plain(headers.headers))
      case Some(507) ⇒ InsufficientStorage(errorBody(dynamicBody), Headers.plain(headers.headers))
    }
  }

  def exceptionToResponse(t: Throwable)(implicit mcf: MessagingContextFactory): Response[Body] = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    t match {
      case noRoute: NoTransportRouteException ⇒ model.NotFound(ErrorBody("not_found", Some("Resource wasn't found"), errorId = errorId))
      case hbEx: HyperBusServerException[ErrorBody] ⇒ hbEx
      case t: Throwable ⇒ model.InternalServerError(ErrorBody("unhandled_exception", Some(t.getMessage + " #"+errorId), errorId = errorId))
    }
  }

  def exceptionToHttpResponse(t: Throwable): HttpResponse = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    t match {
      case noRoute: NoTransportRouteException ⇒ HttpResponse(StatusCodes.NotFound, "Resource wasn't found")
      case hbEx: HyperBusServerException[ErrorBody] ⇒ HttpResponse(StatusCodes.getForKey(hbEx.status).get, hbEx.body.content.asString)
      case t: Throwable ⇒ HttpResponse(StatusCodes.InternalServerError, t.toString + " #" + errorId)
    }
  }

  def unfold(dynamicRequest: DynamicRequest): (TransitionalHeaders, DynamicBody) = {
    dynamicRequest match {
      case DynamicRequest(uri, dynamicBody, headers) ⇒
        (TransitionalHeaders(uri, headers, None), dynamicBody)
    }
  }
  
  def toFrame(message: Message[Body]): Frame = {
    val ba = new ByteArrayOutputStream()
    message.serialize(ba)
    TextFrame(ByteString(ba.toByteArray))
  }

  def toHttpResponse(headers: TransitionalHeaders, body: DynamicBody): HttpResponse = {
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

  def extractRequestHeaders(requestUri: uri.Uri, headers: Headers): TransitionalHeaders = {
    TransitionalHeaders(requestUri, headers, None)
  }

  def extractResponseHeaders(statusCode: Int, headers: Map[String, Seq[String]], messageId: String, correlationId: String): TransitionalHeaders = {
    val responseHeaders = headers + (Header.MESSAGE_ID → Seq(messageId), Header.CORRELATION_ID → Seq(correlationId))
    TransitionalHeaders(null, responseHeaders, Some(statusCode))
  }

  def correlationId(headers: Headers): String = {
    val messageId = headers(Header.MESSAGE_ID)
    headers.get(Header.CORRELATION_ID).getOrElse(messageId).head
  }

  def addField(fieldName: String, fieldValue: String, dynamicRequest: DynamicRequest): DynamicRequest = {
    dynamicRequest match {
      case DynamicRequest(uri, dynamicBody, requestHeader) ⇒
        val fieldMap = dynamicBody.content.asMap
        val updatedBody = DynamicBody(Obj(fieldMap + (fieldName → Text(fieldValue))))
        DynamicRequest(uri, updatedBody, requestHeader)
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

  private def extractHttpHeaders(headers: TransitionalHeaders): List[HttpHeader]= {
    var httpHeaders = List[HttpHeader]()
    headers.headers.foreach {
      case (name, value) ⇒
        if (!isDynamicHeader(name)) httpHeaders = httpHeaders :+ RawHeader(name, value.head)
    }
    httpHeaders
  }
}
