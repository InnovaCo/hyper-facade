package eu.inn.facade.model

import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.MessagingContextFactory
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.HttpRequest

case class FacadeRequestContext(
                                 remoteAddress: String,
                                 httpUri: spray.http.Uri,
                                 pathAndQuery: String,
                                 method: String,
                                 requestHeaders: Map[String, Seq[String]],
                                 stages: Seq[RequestStage]
                               )
{
  def clientCorrelationId: Option[String] = {
    val messageId = requestHeaders.getOrElse(FacadeHeaders.CLIENT_MESSAGE_ID, Seq.empty)
    requestHeaders.getOrElse(FacadeHeaders.CLIENT_CORRELATION_ID, messageId).headOption
  }

  def clientMessagingContext() = {
    MessagingContextFactory.withCorrelationId(clientCorrelationId.getOrElse(IdGenerator.create()))
  }

  def withNextStage(request: FacadeRequest) = copy(
    stages = Seq(RequestStage(request.uri, request.method, request.headers)) ++ stages
  )
}

object FacadeRequestContext {
  def create(remoteAddress: String, httpRequest: HttpRequest, facadeRequest: FacadeRequest) = {
    FacadeRequestContext(
      remoteAddress,
      httpRequest.uri,
      facadeRequest.uri.pattern.specific,
      facadeRequest.method,
      // http headers always override request headers
      // this could be important for WS request
      facadeRequest.headers ++ httpRequest.headers.groupBy(_.name).map { kv ⇒
        kv._1 → kv._2.map(_.value)
      },
      Seq.empty
    )
  }
}

case class RequestStage(
                       requestUri: Uri,
                       requestMethod: String,
                       requestHeaders: Map[String, Seq[String]]
                       )