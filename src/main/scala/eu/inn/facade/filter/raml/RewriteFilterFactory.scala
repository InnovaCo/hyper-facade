package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Annotation, Method, RamlConfigException, RewriteIndexHolder}
import scaldi.{Injectable, Injector}

class RewriteFilterFactory(config: Config)(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
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
