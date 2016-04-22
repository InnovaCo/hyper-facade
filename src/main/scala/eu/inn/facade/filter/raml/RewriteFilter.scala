package eu.inn.facade.filter.raml

import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.UriTransformer

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(val args: rewrite) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenUri = UriTransformer.rewriteOneStepForward(request.uri, args.getUri)
    val rewrittenRequest = request.copy(
      uri = rewrittenUri
    )
    Future.failed(new FilterRestartException(rewrittenRequest, "rewrite"))
  }
}

class RewriteEventFilter(val args: rewrite) extends EventFilter {
  override def apply(context: FacadeRequestContext, event: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    //val rewrittenUri = UriTransformer.rewriteOneStepBack(context.method)(event.uri)
    val rewrittenUri = UriTransformer.rewriteOneStepForward(event.uri, args.getUri)
    val rewrittenEvent = event.copy(
      uri = rewrittenUri
    )
    Future.failed(new FilterRestartException(rewrittenEvent, "rewrite"))
  }
}
