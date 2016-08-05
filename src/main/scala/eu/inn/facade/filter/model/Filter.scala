package eu.inn.facade.filter.model

import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.raml.{Annotation, Field, RamlAnnotation}

trait Filter

trait RamlFilterFactory {
  import eu.inn.facade.filter.model.RamlTarget.annotations

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
    val l = rawFilters.foldLeft(Seq.newBuilder[RequestFilter]) { (wrappedFilters, rawFilter) ⇒
      annotations(ramlTarget).foldLeft(wrappedFilters) { (wrappedFilters, annotation) ⇒
        wrappedFilters += ConditionalRequestFilterWrapper(annotation, rawFilter, predicateEvaluator)
      }
    }.result()
    l
  }

  def wrapResponseFilters(rawFilters: Seq[ResponseFilter], ramlTarget: RamlTarget, predicateEvaluator: PredicateEvaluator): Seq[ResponseFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[ResponseFilter]) { (wrappedFilters, rawFilter) ⇒
      annotations(ramlTarget).foldLeft(wrappedFilters) { (wrappedFilters, annotation) ⇒
        wrappedFilters += ConditionalResponseFilterWrapper(annotation, rawFilter, predicateEvaluator)
      }
    }.result()
  }

  def wrapEventFilters(rawFilters: Seq[EventFilter], ramlTarget: RamlTarget, predicateEvaluator: PredicateEvaluator): Seq[EventFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[EventFilter]) { (wrappedFilters, rawFilter) ⇒
      annotations(ramlTarget).foldLeft(wrappedFilters) { (wrappedFilters, annotation) ⇒
        wrappedFilters += ConditionalEventFilterWrapper(annotation, rawFilter, predicateEvaluator)
      }
    }.result()
  }
}

sealed trait RamlTarget
object RamlTarget {
  def annotations(ramlTarget: RamlTarget): Seq[Option[RamlAnnotation]] = {
    ramlTarget match {
      case TargetResource(_, Annotation(_, ann)) ⇒
        Seq(ann)
      case TargetMethod(_, _, Annotation(_, ann)) ⇒
        Seq(ann)
      case TargetField(_, field) ⇒
        field.annotations.foldLeft(Seq.newBuilder[Option[RamlAnnotation]]) { (ramlAnnotations, fieldAnnotation) ⇒
          ramlAnnotations += fieldAnnotation.value
        }.result()
    }
  }
}

case class TargetResource(uri: String, annotation: Annotation) extends RamlTarget
case class TargetMethod(uri: String, method: String, annotation: Annotation) extends RamlTarget
case class TargetField(typeName: String, field: Field) extends RamlTarget