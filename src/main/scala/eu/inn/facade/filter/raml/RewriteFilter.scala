package eu.inn.facade.filter.raml

import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.UriTransformer
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(val args: rewrite) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenUri = Uri(UriTransformer.rewrite(request.uri, Uri(args.getUri)).formatted)
    val rewrittenRequest = request.copy(
      uri = rewrittenUri
    )
    Future.successful(rewrittenRequest)
  }
}

class RewriteEventFilter extends EventFilter {
  override def apply(context: FacadeRequestContext, stage: RequestStage, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {

    val rewrittenEvent = event.copy(
      uri = stage.requestUri
    )

    Future.successful(rewrittenEvent)

    /*

    val rewrittenUri = UriTransformer.rewrite(event.uri, Uri(args.getUri))
    //val rewrittenUri = UriTransformer.rewriteOneStepForward(event.uri, args.getUri)
    val rewrittenEvent = event.copy(
      uri = rewrittenUri
    )
    Future.failed(new FilterRestartException(rewrittenEvent, "rewrite"))
    */
  }
}
