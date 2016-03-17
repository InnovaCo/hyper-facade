package eu.inn.facade.model

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization.StringDeserializer
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.{HttpRequest, HttpResponse}

trait FacadeMessage {
  def headers: Map[String, Seq[String]]
  def body: Value
}

case class FacadeRequest(uri: Uri, method: String, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicRequest: DynamicRequest = {
    DynamicRequest(uri, DynamicBody(body),
      new HeadersBuilder(FacadeRequest.facadeHeadersToHyperbus(headers))
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

object FacadeRequest {
  def apply(uri: Uri, request: HttpRequest): FacadeRequest = {
    FacadeRequest(uri,
      request.method.name,
      request.headers.map { kv ⇒
        kv.name → kv.value
      } groupBy (_._1)  map { kv ⇒
        kv._1 → kv._2.map(_._2)
      },
    StringDeserializer.dynamicBody(Some(request.entity.asString)).content
    )
  }

  def apply(request: DynamicRequest): FacadeRequest = {
    FacadeRequest(
      request.uri,
      request.method,
      hyperBusHeadersToFacade(request.headers),
      request.body.content
    )
  }

  private def facadeHeadersToHyperbus(headers: Map[String, Seq[String]]): Map[String, Seq[String]] = {
    headers.foldLeft(Map.newBuilder[String, Seq[String]]) {
      case (newHeaders, (Header.CONTENT_TYPE, value :: tail))
        if value.startsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_START)
          && value.endsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_END) ⇒

        val beginIndex = FacadeHeaders.CERTAIN_CONTENT_TYPE_START.length
        val endIndex = value.length - FacadeHeaders.CERTAIN_CONTENT_TYPE_END.length

        newHeaders += Header.CONTENT_TYPE → Seq(value.substring(beginIndex, endIndex))

      case (newHeaders, (k, v)) if directFacadeToHyperBus.contains(k) ⇒
        newHeaders += directFacadeToHyperBus(k) → v
    } result()
  }

  private def hyperBusHeadersToFacade(headers: Map[String, Seq[String]]): Map[String, Seq[String]] = {
    headers.foldLeft(Map.newBuilder[String, Seq[String]]) {
      case (newHeaders, (Header.CONTENT_TYPE, value :: tail)) ⇒
        newHeaders += Header.CONTENT_TYPE → Seq(
          FacadeHeaders.CERTAIN_CONTENT_TYPE_START + value + FacadeHeaders.CERTAIN_CONTENT_TYPE_END
        )

      case (newHeaders, (k, v)) if directHyperBusToFacade.contains(k) ⇒
        newHeaders += directHyperBusToFacade(k) → v
    } result()
  }

  private val directHeaderMapping = Seq(
    FacadeHeaders.CLIENT_CORRELATION_ID → Header.CORRELATION_ID,
    FacadeHeaders.CLIENT_MESSAGE_ID → Header.MESSAGE_ID,
    FacadeHeaders.CLIENT_REVISION → Header.REVISION
  )
  private val directFacadeToHyperBus = directHeaderMapping.toMap
  private val directHyperBusToFacade = directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}


case class FacadeResponse(status: Int, headers: Map[String, Seq[String]], body: Value) extends FacadeMessage {
  def toDynamicResponse: Response[DynamicBody] = ???
  def toHttpResponse: HttpResponse = ???
}

object FacadeResponse {
  def apply(response: Response[DynamicBody]): FacadeResponse = ???
}

class FilterInterruptException(val response: FacadeResponse,
                               message: String,
                               cause: Throwable = null) extends Exception (message, cause)
