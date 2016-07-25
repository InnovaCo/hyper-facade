package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.deny
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

class DenyFilterFactory(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetFields(_, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new DenyResponseFilter(fields, predicateEvaluator)),
          eventFilters = Seq(new DenyEventFilter(fields, predicateEvaluator))
        )

      case TargetResource(_, Annotation(_, Some(deny: deny))) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, Annotation(_, Some(deny: deny))) ⇒
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
