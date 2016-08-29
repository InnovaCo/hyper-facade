package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.model._
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.raml._
import scaldi.{Injectable, Injector}

class RewriteFilterFactory(config: Config)(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    val (rewrittenUri, originalUri, ramlMethod) = target match {
      case TargetResource(uri, RewriteAnnotation(_, _, newUri)) ⇒ (newUri, uri, None)
      case TargetMethod(uri, method, RewriteAnnotation(_, _, newUri)) ⇒ (newUri, uri, Some(Method(method)))
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'rewrite' cannot be assigned to $otherTarget")
    }
    RewriteIndexHolder.updateRewriteIndex(originalUri, rewrittenUri, ramlMethod)
    SimpleFilterChain(
      requestFilters = Seq(new RewriteRequestFilter(rewrittenUri)),
      responseFilters = Seq.empty,
      eventFilters = Seq(new RewriteEventFilter)
    )
  }
}
