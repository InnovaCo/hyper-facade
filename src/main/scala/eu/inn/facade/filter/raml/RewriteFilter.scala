package eu.inn.facade.filter.raml

import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.{HalTransformer, UriTransformer}
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(args: rewrite) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenUri = UriTransformer.rewriteOneStepForward(request.uri, args.getUri)

    val uriTransformer: (Uri ⇒ Uri) = UriTransformer.rewriteForward(request.method)
    val transformedBody = HalTransformer.transformEmbeddedObject(request.body, uriTransformer)

    val rewrittenRequest = request.copy(
      uri = rewrittenUri,
      body = transformedBody
    )
    Future.failed(new FilterRestartException(rewrittenRequest, "rewrite"))
  }
}

class RewriteResponseFilter extends ResponseFilter {
  override def apply(context: FacadeRequestContext, response: FacadeResponse)(implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val uriTransformer: (Uri ⇒ Uri) = UriTransformer.rewriteToOriginal(context.method)
      val transformedBody = HalTransformer.transformEmbeddedObject(response.body, uriTransformer)

      response.copy(
        body = transformedBody
      )
    }
  }
}

class RewriteEventFilter extends EventFilter {
  override def apply(context: FacadeRequestContext, event: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenUri = UriTransformer.rewriteOneStepBack(context.method)(event.uri)
    val uriTransformer: (Uri ⇒ Uri) = UriTransformer.rewriteToOriginal(context.method)
    val transformedBody = HalTransformer.transformEmbeddedObject(event.body, uriTransformer)

    val rewrittenEvent = event.copy(
      uri = rewrittenUri,
      body = transformedBody
    )
    Future.failed(new FilterRestartException(rewrittenEvent, "rewrite"))
  }
}
