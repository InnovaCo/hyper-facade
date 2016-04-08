package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.rewrite

class RewriteFilterFactory extends RamlFilterFactory {
  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val rewriteArgs = target match {
      case TargetResource(_, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ Some(rewriteArgs)
      case TargetMethod(_, _, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ Some(rewriteArgs)
      case _ ⇒ None
    }
    rewriteArgs map { args ⇒
        SimpleFilterChain(
          requestFilters = Seq(new RewriteRequestFilter(args)),
          responseFilters = Seq(new RewriteResponseFilter),
          eventFilters = Seq(new RewriteEventFilter)
        )
    } getOrElse {
      FilterChain.empty
    }
  }
}