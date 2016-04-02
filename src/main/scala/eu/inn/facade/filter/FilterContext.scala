package eu.inn.facade.filter

import eu.inn.facade.model.FacadeHeaders
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.MessagingContextFactory
import eu.inn.hyperbus.transport.api.uri.Uri

case class FilterContext(
                                 originalPath: String,
                                 originalMethod: String,
                                 originalRequestHeaders: Map[String, Seq[String]],

                                 // todo: make these optional?
                                 requestUri: Uri,
                                 requestMethod: String,
                                 requestHeaders: Map[String, Seq[String]]
                                )
{
  def clientCorrelationId: Option[String] = {
    val messageId = originalRequestHeaders.getOrElse(FacadeHeaders.CLIENT_MESSAGE_ID, Seq.empty)
    originalRequestHeaders.getOrElse(FacadeHeaders.CLIENT_CORRELATION_ID, messageId).headOption
  }


  def clientMessagingContext() = {
    MessagingContextFactory.withCorrelationId(clientCorrelationId.getOrElse(IdGenerator.create()))
  }
}