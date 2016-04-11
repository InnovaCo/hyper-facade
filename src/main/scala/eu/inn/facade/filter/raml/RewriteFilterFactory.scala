package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Annotation, Method, RamlConfigException}

class RewriteFilterFactory extends RamlFilterFactory {
  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val (rewriteArgs, originalUri, ramlMethod) = target match {
      case TargetResource(uri, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ (rewriteArgs, uri, None)
      case TargetMethod(uri, method, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ (rewriteArgs, uri, Some(Method(method)))
      case otherTarget ⇒ throw new RamlConfigException(s"Annotation 'rewrite' cannot be assigned to $otherTarget")
    }
    RewriteIndexHolder.updateRewriteIndex(originalUri, rewriteArgs.getUri, ramlMethod)
    SimpleFilterChain(
      requestFilters = Seq(new RewriteRequestFilter(rewriteArgs)),
      responseFilters = Seq(new RewriteResponseFilter),
      eventFilters = Seq(new RewriteEventFilter)
    )
  }
}