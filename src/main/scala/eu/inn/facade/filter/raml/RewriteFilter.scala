package eu.inn.facade.filter.raml

import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.UriTransformer
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(val args: rewrite) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenUri = UriTransformer.rewrite(request.uri, Uri(args.getUri))
    val rewrittenRequest = request.copy(
      uri = rewrittenUri
    )
    Future.successful(rewrittenRequest)
  }
}

class RewriteEventFilter(val args: rewrite, rewriteCountLimit: Int) extends EventFilter {
  override def apply(context: FacadeRequestContext, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val newUri = UriTransformer.rewriteBackward(event.uri, event.method)
    Future.successful(event.copy(uri = newUri))
  }
}
