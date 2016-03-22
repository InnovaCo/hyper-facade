package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model.{FacadeResponse, _}
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.hyperbus.model.{ErrorBody, NotFound}
import eu.inn.hyperbus.transport.api.uri.Uri

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
  override def apply(context: RequestFilterContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenRequest = request.copy(
      uri = Uri(args.getUri)
    )
    Future.failed(new FilterRestartException(rewrittenRequest, "Rewrite"))
  }
}