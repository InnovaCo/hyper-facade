package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Annotation, Method, RamlConfigException, RewriteIndexHolder}

class RewriteRequestFilterFactory(config: Config) extends RamlFilterFactory {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val (rewriteArgs, originalUri, ramlMethod) = target match {
      case TargetResource(uri, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ (rewriteArgs, uri, None)
      case TargetMethod(uri, method, Annotation(_, Some(rewriteArgs: rewrite))) ⇒ (rewriteArgs, uri, Some(Method(method)))
      case otherTarget ⇒ throw new RamlConfigException(s"Annotation 'rewrite' cannot be assigned to $otherTarget")
    }
    RewriteIndexHolder.updateRewriteIndex(originalUri, rewriteArgs.getUri, ramlMethod)
    SimpleFilterChain(
      requestFilters = Seq(new RewriteRequestFilter(rewriteArgs)),
      responseFilters = Seq.empty,
      eventFilters = Seq(new RewriteEventFilter)
    )
  }
}
