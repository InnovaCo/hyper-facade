package eu.inn.facade.filter.model

import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.raml.{Annotation, Field, RamlAnnotation}

trait Filter

trait RamlFilterFactory {
  import eu.inn.facade.filter.model.RamlTarget.annotation

  def createFilters(target: RamlTarget): SimpleFilterChain
  def predicateEvaluator: PredicateEvaluator

  final def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val rawFilterChain = createFilters(target)
    SimpleFilterChain (
      requestFilters = wrapRequestFilters(rawFilterChain.requestFilters, target, predicateEvaluator),
      responseFilters = wrapResponseFilters(rawFilterChain.responseFilters, target, predicateEvaluator),
      eventFilters = wrapEventFilters(rawFilterChain.eventFilters, target, predicateEvaluator)
    )
  }

  def wrapRequestFilters(rawFilters: Seq[RequestFilter], ramlTarget: RamlTarget, predicateEvaluator: PredicateEvaluator): Seq[RequestFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[RequestFilter]) { (wrappedFilters, rawFilter) ⇒
      wrappedFilters += ConditionalRequestFilterWrapper(annotation(ramlTarget), rawFilter, predicateEvaluator)
    }.result()
  }

  def wrapResponseFilters(rawFilters: Seq[ResponseFilter], ramlTarget: RamlTarget, predicateEvaluator: PredicateEvaluator): Seq[ResponseFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[ResponseFilter]) { (wrappedFilters, rawFilter) ⇒
      wrappedFilters += ConditionalResponseFilterWrapper(annotation(ramlTarget), rawFilter, predicateEvaluator)
    }.result()
  }

  def wrapEventFilters(rawFilters: Seq[EventFilter], ramlTarget: RamlTarget, predicateEvaluator: PredicateEvaluator): Seq[EventFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[EventFilter]) { (wrappedFilters, rawFilter) ⇒
      wrappedFilters += ConditionalEventFilterWrapper(annotation(ramlTarget), rawFilter, predicateEvaluator)
    }.result()
  }
}

sealed trait RamlTarget
object RamlTarget {
  def annotation(ramlTarget: RamlTarget): Option[RamlAnnotation] = {
    ramlTarget match {
      case TargetResource(_, Annotation(_, ann)) ⇒
        ann
      case TargetMethod(_, _, Annotation(_, ann)) ⇒
        ann
      case _ ⇒
        None  // we shouldn't wrap filters with target = 'field' because in this case we there can be multiple targets with different predicates
    }
  }
}

case class TargetResource(uri: String, annotation: Annotation) extends RamlTarget
case class TargetMethod(uri: String, method: String, annotation: Annotation) extends RamlTarget
//case class TargetResponse(uri: String, code: Int) extends RamlTarget
//RequestBody
//ResponseBody
case class TargetFields(typeName: String, fields: Seq[Field]) extends RamlTarget