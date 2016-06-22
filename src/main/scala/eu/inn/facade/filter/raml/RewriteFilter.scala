package eu.inn.facade.filter.raml

import eu.inn.facade.filter.model.{EventFilter, RequestFilter}
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.UriTransformer
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(val args: rewrite) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val request = contextWithRequest.request
      val rewrittenUri = UriTransformer.rewrite(request.uri, Uri(args.getUri))
      val rewrittenRequest = request.copy(
        uri = rewrittenUri
      )
      contextWithRequest.copy(
        request = rewrittenRequest
      )
    }
  }
}

class RewriteEventFilter extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val newUri = UriTransformer.rewriteBackward(event.uri, event.method)
    Future.successful(event.copy(uri = newUri))
  }
}
