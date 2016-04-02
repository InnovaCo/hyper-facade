package eu.inn.facade.model

import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.MessagingContextFactory
import eu.inn.hyperbus.transport.api.uri.Uri

case class FacadeRequestContext(
                          originalPath: String,
                          originalMethod: String,
                          originalRequestHeaders: Map[String, Seq[String]],

                          prepared: Option[PreparedRequestContext]
                        )
{
  def clientCorrelationId: Option[String] = {
    val messageId = originalRequestHeaders.getOrElse(FacadeHeaders.CLIENT_MESSAGE_ID, Seq.empty)
    originalRequestHeaders.getOrElse(FacadeHeaders.CLIENT_CORRELATION_ID, messageId).headOption
  }

  def clientMessagingContext() = {
    MessagingContextFactory.withCorrelationId(clientCorrelationId.getOrElse(IdGenerator.create()))
  }

  def prepare(request: FacadeRequest) = copy(
    prepared = Some(PreparedRequestContext(request.uri, request.method, request.headers))
  )
}

object FacadeRequestContext {
  def create(originalRequest: FacadeRequest) = {
    FacadeRequestContext(
      originalRequest.uri.pattern.specific,
      originalRequest.method,
      originalRequest.headers,
      None
    )
  }
}

case class PreparedRequestContext(
                                   requestUri: Uri,
                                   requestMethod: String,
                                   requestHeaders: Map[String, Seq[String]]
                                 )