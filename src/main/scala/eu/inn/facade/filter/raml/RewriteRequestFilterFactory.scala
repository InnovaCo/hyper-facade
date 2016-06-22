package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.model.{RamlFilterFactory, RamlTarget, TargetMethod, TargetResource}
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Annotation, Method, RamlConfigException, RewriteIndexHolder}

class RewriteRequestFilterFactory(config: Config) extends RamlFilterFactory {

  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val (rewriteArgs, originalUri, ramlMethod) = target match {
      case TargetResource(uri, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ (rewriteArgs, uri, None)
      case TargetMethod(uri, method, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ (rewriteArgs, uri, Some(Method(method)))
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'rewrite' cannot be assigned to $otherTarget")
    }
    RewriteIndexHolder.updateRewriteIndex(originalUri, rewriteArgs.getUri, ramlMethod)
    SimpleFilterChain(
      requestFilters = Seq(new RewriteRequestFilter(rewriteArgs)),
      responseFilters = Seq.empty,
      eventFilters = Seq(new RewriteEventFilter)
    )
  }
}
