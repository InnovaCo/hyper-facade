package eu.inn.facade.http

import java.io.ByteArrayOutputStream

import akka.util.ByteString
import eu.inn.binders.dynamic.{Null, Obj, Text}
import eu.inn.binders.json._
import eu.inn.hyperbus._
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import org.slf4j.LoggerFactory
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.http._

object RequestMapper {

  val log = LoggerFactory.getLogger(RequestMapper.getClass)

  def toDynamicRequest(frame: Frame): DynamicRequest = {
    DynamicRequest(frame.payload.iterator.asInputStream)
  }


  def exceptionToResponse(t: Throwable)(implicit mcf: MessagingContextFactory): Response[DynamicBody] = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    t match {
      case noRoute: NoTransportRouteException ⇒ model.NotFound(ErrorBody("not_found", Some("Resource wasn't found"), errorId = errorId))
      case hbEx: HyperBusException[ErrorBody] ⇒ hbEx
      case t: Throwable ⇒ model.InternalServerError(ErrorBody("internal_server_error", Some(s"Unhandled error #$errorId"), errorId = errorId))
    }
  }

  def exceptionToHttpResponse(t: Throwable): HttpResponse = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    t match {
      case noRoute: NoTransportRouteException ⇒ HttpResponse(StatusCodes.NotFound, "Resource not found")
      case hbEx: HyperBusException[ErrorBody] ⇒ HttpResponse(StatusCodes.getForKey(hbEx.status).get, hbEx.body.toJson)
      case t: Throwable ⇒ HttpResponse(StatusCodes.InternalServerError, s"Unhandled error #$errorId")
    }
  }
  
  def toFrame(message: Message[Body]): Frame = {
    val ba = new ByteArrayOutputStream()
    message.serialize(ba)
    TextFrame(ByteString(ba.toByteArray))
  }

  /*
  def toHttpResponse(headers: TransitionalHeaders, body: DynamicBody): HttpResponse = {
    val statusCode = StatusCode.int2StatusCode(headers.statusCode getOrElse 200)
    val httpContentType: ContentType = contentType(headers.headerOption(Header.CONTENT_TYPE))
    val jsonBody = body.content.toJson
    HttpResponse(statusCode, HttpEntity(httpContentType, jsonBody), extractHttpHeaders(headers))
  }

  def toFailedHttpResponse(statusCode: Int, message: String): HttpResponse = {
    val httpStatusCode = StatusCode.int2StatusCode(statusCode)
    val httpContentType: ContentType = contentType(None)
    val jsonBody = Text(message).toJson
    HttpResponse(httpStatusCode, HttpEntity(httpContentType, jsonBody), List())
  }
*/

  def correlationId(headers: Headers): String = {
    val messageId = headers(Header.MESSAGE_ID)
    headers.get(Header.CORRELATION_ID).getOrElse(messageId).head
  }

  private def errorBody(dynamicBody: DynamicBody): ErrorBody = { // todo: reuse in exceptions!
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
}
