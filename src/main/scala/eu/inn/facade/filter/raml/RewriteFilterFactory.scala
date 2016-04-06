package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.UriRewriter
import eu.inn.hyperbus.transport.api.uri.UriParser

import scala.concurrent.{ExecutionContext, Future}

class RewriteFilterFactory extends RamlFilterFactory {
  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val rewriteArgs = target match {
      case TargetResource(_, Annotation(_, Some(rewriteArgs : rewrite))) ⇒ Some(rewriteArgs)
      case TargetMethod(_, _, Annotation(_, Some(rewriteArgs : rewrite))) ⇒ Some(rewriteArgs)
      case _ ⇒ None
    }
    rewriteArgs map { args ⇒
      SimpleFilterChain(
        requestFilters = Seq(new RewriteRequestFilter(args)),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )
    } getOrElse {
      FilterChain.empty
    }
  }
}

class RewriteRequestFilter(args: rewrite) extends RequestFilter {
  val uriParameters = UriParser.extractParameters(args.getUri)

  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewriteResult = UriRewriter.rewrite(request.uri, args.getUri, uriParameters)

    if (rewriteResult.failures.isEmpty) {
      val rewrittenRequest = request.copy(
        uri = rewriteResult.uri
      )
      Future.failed(new FilterRestartException(rewrittenRequest, "rewrite"))
    } else {
      Future.failed(rewriteResult.failures.head)
    }
  }
}