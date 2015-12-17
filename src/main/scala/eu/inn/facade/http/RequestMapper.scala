package eu.inn.facade.http

import java.io.ByteArrayOutputStream

import akka.util.ByteString
import eu.inn.binders.dynamic.Text
import eu.inn.binders.json._
import eu.inn.facade.filter.model.DynamicRequestHeaders._
import eu.inn.facade.filter.model.Headers
import eu.inn.hyperbus.model.{Body, DynamicBody, DynamicRequest, Message}
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
    DynamicRequest(httpRequest.entity.data.toByteString.iterator.asInputStream)
  }

  def unfold(dynamicRequest: DynamicRequest): (Headers, DynamicBody) = {
    dynamicRequest match {
      case DynamicRequest(requestHeader, dynamicBody) ⇒ (extractHeaders(requestHeader), dynamicBody)
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
    headers += ((URL, dynamicHeader.url))
    headers += ((METHOD, dynamicHeader.method))
    dynamicHeader.contentType match {
      case Some(contentType) ⇒ headers += ((CONTENT_TYPE, contentType))
      case None ⇒
    }
    headers += ((MESSAGE_ID, dynamicHeader.messageId))
    dynamicHeader.correlationId match {
      case Some(correlationId) ⇒ headers += ((CORRELATION_ID, correlationId))
      case None ⇒
    }
    Headers(headers)
  }

  def extractDynamicHeader(headers: Headers): RequestHeader = {
    var contentTypeOption: Option[String] = None
    val contentType = headers → CONTENT_TYPE
    if (contentType != null) contentTypeOption = Some(contentType)
    var correlationIdOption: Option[String] = None
    val correlationId = headers → CORRELATION_ID
    if (correlationId != null) correlationIdOption = Some(correlationId)
    RequestHeader(headers → URL, headers → METHOD, contentTypeOption, headers → MESSAGE_ID, correlationIdOption)
  }

  private def contentType(contentType: Option[String]): ContentType = {
    contentType match {
      case None ⇒ `application/json`
      case Some(dynamicContentType) ⇒
        val mediaType = MediaTypes.register(MediaType.custom(dynamicContentType, null, true, false, Seq("json")))
        ContentType(mediaType, Some(`UTF-8`))
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
}
