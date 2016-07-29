package eu.inn.facade.model

import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.MessagingContextFactory
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.{HttpHeader, HttpRequest}

case class FacadeRequestContext(
                                 remoteAddress: String,
                                 httpUri: spray.http.Uri,
                                 pathAndQuery: String,
                                 method: String,
                                 requestHeaders: Map[String, Seq[String]],
                                 prepared: Option[PreparedRequestContext],
                                 contextStorage: Map[String, Any]
                               )
{
  def clientCorrelationId: Option[String] = {
    val messageId = requestHeaders.getOrElse(FacadeHeaders.CLIENT_MESSAGE_ID, Seq.empty)
    requestHeaders.getOrElse(FacadeHeaders.CLIENT_CORRELATION_ID, messageId).headOption
  }

  def clientMessagingContext() = {
    MessagingContextFactory.withCorrelationId(clientCorrelationId.getOrElse(IdGenerator.create()))
  }

  def prepare(request: FacadeRequest) = copy(
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
      facadeRequest.headers ++ normalizeHeaders(httpRequest.headers),
      None,
      Map.empty
    )
  }

  def normalizeHeaders(headers: List[HttpHeader]): Map[String, Seq[String]] = {
    headers.foldLeft(Map.newBuilder[String, Seq[String]]) { (facadeRequestHeaders, httpHeader) ⇒
      facadeRequestHeaders += (httpHeader.name → Seq(httpHeader.value))
    }.result()
  }

}

// todo: better name?
case class PreparedRequestContext(
                                   requestUri: Uri,
                                   requestMethod: String,
                                   requestHeaders: Map[String, Seq[String]]
                                 )

// todo: better name?
case class RequestStage(
                         requestUri: Uri,
                         requestMethod: String
                       )

// todo: better name?
case class ContextWithRequest(context: FacadeRequestContext, stages: Seq[RequestStage], request: FacadeRequest) {
  def withNextStage(nextRequest: FacadeRequest): ContextWithRequest = copy(
    context = context.prepare(nextRequest),
    stages = Seq(RequestStage(nextRequest.uri, nextRequest.method)) ++ stages,
    request = nextRequest
  )
}

object ContextWithRequest {
  def apply(context: FacadeRequestContext, request: FacadeRequest): ContextWithRequest = new ContextWithRequest(context, Seq.empty, request)
}
