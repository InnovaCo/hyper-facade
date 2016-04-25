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
                                 prepared: Option[PreparedRequestContext]
                               )
{
  def clientCorrelationId: Option[String] = {
    val messageId = requestHeaders.getOrElse(FacadeHeaders.CLIENT_MESSAGE_ID, Seq.empty)
    requestHeaders.getOrElse(FacadeHeaders.CLIENT_CORRELATION_ID, messageId).headOption
  }

  def clientMessagingContext() = {
    MessagingContextFactory.withCorrelationId(clientCorrelationId.getOrElse(IdGenerator.create()))
  }

  def prepareNext(request: FacadeRequest) = copy(
    prepared = Some(PreparedRequestContext(request.uri, request.method, request.headers))
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
      None
    )
  }
}

case class PreparedRequestContext(
                                   requestUri: Uri,
                                   requestMethod: String,
                                   requestHeaders: Map[String, Seq[String]]
                                 )

case class RequestStage(
                         requestUri: Uri,
                         requestMethod: String
                       )

// todo: name
case class FCT(context: FacadeRequestContext, stages: Seq[RequestStage], request: FacadeRequest) {
  def withNextStage(nextRequest: FacadeRequest): FCT = copy(
    context = context.prepareNext(nextRequest),
    stages = Seq(RequestStage(nextRequest.uri, nextRequest.method)) ++ stages,
    request = nextRequest
  )
}

object FCT {
  def apply(context: FacadeRequestContext, request: FacadeRequest): FCT = new FCT(context, Seq.empty, request)
}
