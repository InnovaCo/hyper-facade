package eu.inn.facade.model

import eu.inn.binders.dynamic.{Null, Value}
import eu.inn.binders.naming.SnakeCaseToCamelCaseConverter
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization.{ResponseHeader, StringDeserializer}
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._
import eu.inn.binders.json._

trait FacadeMessage {
  def headers: Map[String, Seq[String]]
  def body: Value
}

case class FacadeRequest(uri: Uri, method: String, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicRequest: DynamicRequest = {
    DynamicRequest(uri, DynamicBody(body),
      new HeadersBuilder(headers)
      .withMethod(method)
      .result()
    )
  }

  def correlationId: String = {
    val messageId = headers(Header.MESSAGE_ID)
    headers.getOrElse(Header.CORRELATION_ID, messageId).head
  }

  def contentType: Option[String] = {
    headers.get(Header.CONTENT_TYPE).flatMap(_.headOption)
  }
}

// todo: converters make explicit

object FacadeRequest {
  val snakeCaseToCamelCase = new SnakeCaseToCamelCaseConverter

  def apply(request: HttpRequest, remoteAddress: String): FacadeRequest = {
    FacadeRequest(Uri(request.uri.toString),
      request.method.name,
      request.headers.groupBy (_.name) map { kv ⇒
        snakeCaseToCamelCase.convert(kv._1) → kv._2.map(_.value)
      } += ("X-Forwarded-For" → Seq(remoteAddress))
      ,
      if (request.entity.nonEmpty){
        StringDeserializer.dynamicBody(Some(request.entity.asString)).content
      }
      else {
        Null
      }
    )
  }

  def apply(request: DynamicRequest): FacadeRequest = {
    FacadeRequest(
      request.uri,
      request.method,
      request.headers.filterNot(_ == Header.METHOD),
      request.body.content
    )
  }
}


case class FacadeResponse(status: Int, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicResponse: Response[DynamicBody] = {
    StandardResponse(
      ResponseHeader(status, headers),
      DynamicBody(body)
    ).asInstanceOf[Response[DynamicBody]]
  }

  def toHttpResponse: HttpResponse = {
    val statusCode = StatusCode.int2StatusCode(status)
    val contentType = contentTypeToSpray(headers.get(Header.CONTENT_TYPE).flatMap(_.headOption))
    val jsonBody = body.toJson
    HttpResponse(statusCode, HttpEntity(contentType, jsonBody), headers.flatMap{ case (name, values) ⇒
      values.map { value ⇒
        RawHeader(name, value)
      }
    }.toList)
  }

  private def contentTypeToSpray(contentType: Option[String]): spray.http.ContentType = {
    contentType match {
      case None ⇒ `application/json`
      case Some(dynamicContentType) ⇒
        val indexOfSlash = dynamicContentType.indexOf('/')
        val (mainType, subType) = indexOfSlash match {
          case -1 ⇒
            (dynamicContentType, "")
          case index ⇒
            val mainType = dynamicContentType.substring(0, indexOfSlash)
            val subType = dynamicContentType.substring(indexOfSlash + 1)
            (mainType, subType)
        }
        // todo: why we need to register??? replace with header?
        val mediaType = MediaTypes.register(MediaType.custom(mainType, subType, compressible = true, binary = false))
        spray.http.ContentType(mediaType, `UTF-8`)
    }
  }
}

object FacadeResponse {
  def apply(response: Response[DynamicBody]): FacadeResponse = {
    FacadeResponse(response.status, response.headers, response.body.content)
  }
}

class FilterInterruptException(val response: FacadeResponse,
                               message: String,
                               cause: Throwable = null) extends Exception (message, cause)

class FilterRestartException(val request: FacadeRequest,
                               message: String,
                               cause: Throwable = null) extends Exception (message, cause)

class RestartLimitReachedException(num: Int, max: Int) extends Exception (s"Maximum ($max) restart limits exceeded ($num)")