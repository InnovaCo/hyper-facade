package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text, Value}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model.{RamlFilterFactory, RamlTarget, RequestFilter, TargetFields}
import eu.inn.facade.model._
import eu.inn.facade.raml.{Annotation, Field}

import scala.concurrent.{ExecutionContext, Future}

class EnrichmentFilterFactory extends RamlFilterFactory {
  def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetFields(typeName, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new EnrichRequestFilter(fields)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )
      case _ ⇒ FilterChain.empty // log warning
    }
  }
}

class EnrichRequestFilter(val fields: Seq[Field]) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val request = contextWithRequest.request
      val enrichedFields = enrichFields(fields, request.body.asMap, contextWithRequest.context)
      contextWithRequest.copy(
        request = request.copy(body = Obj(enrichedFields))
      )
    }
  }

  private def enrichFields(ramlFields: Seq[Field], fields: scala.collection.Map[String, Value], context: FacadeRequestContext): scala.collection.Map[String, Value] = {
    ramlFields.foldLeft(fields) { (notEnrichedFields, ramlField) ⇒
      val annotations = ramlField.annotations
      var enrichedFields = notEnrichedFields
      annotations.foreach { annotation ⇒
        annotation.name match {
          case Annotation.CLIENT_IP ⇒
            enrichedFields += ramlField.name → Text(context.remoteAddress)

          case Annotation.CLIENT_LANGUAGE ⇒
            context.requestHeaders.get(FacadeHeaders.CLIENT_LANGUAGE) match {
              case Some(value :: _) ⇒ enrichedFields += ramlField.name → Text(value) // todo: format of header?
              case _ ⇒ // do nothing
            }

          case _ ⇒ // do nothing, this annotation doesn't belong to enrichment filter
        }
      }
      if (shouldFilterNestedFields(ramlField, enrichedFields)) {
        val fieldName = ramlField.name
        val nestedFields = enrichedFields(fieldName).asMap
        val enrichedNestedFields = enrichFields(ramlField.fields, nestedFields, context)
        enrichedFields + (fieldName → Obj(enrichedNestedFields))
      } else
        enrichedFields
    }
  }

  def shouldFilterNestedFields(ramlField: Field, fields: scala.collection.Map[String, Value]): Boolean = {
    ramlField.fields.nonEmpty &&
      fields.nonEmpty &&
      fields.contains(ramlField.name)
  }
}
