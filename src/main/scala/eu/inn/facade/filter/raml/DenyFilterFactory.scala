package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.raml.DenyAnnotation
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

class DenyFilterFactory(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetField(_, field) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new DenyResponseFilter(field, predicateEvaluator)),
          eventFilters = Seq(new DenyEventFilter(field, predicateEvaluator))
        )

      case TargetResource(_, DenyAnnotation(_, _)) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, DenyAnnotation(_, _)) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (deny) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}
