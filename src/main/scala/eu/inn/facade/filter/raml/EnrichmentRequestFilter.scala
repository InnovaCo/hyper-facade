package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text, Value}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.model._
import eu.inn.facade.raml.{Annotation, Field}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

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
      if (shouldVisitNestedFields(ramlField, enrichedFields)) {
        val fieldName = ramlField.name
        val nestedFields = enrichedFields(fieldName).asMap
        val enrichedNestedFields = enrichFields(ramlField.fields, nestedFields, context)
        enrichedFields + (fieldName → Obj(enrichedNestedFields))
      } else
        enrichedFields
    }
  }

  def shouldVisitNestedFields(ramlField: Field, fields: scala.collection.Map[String, Value]): Boolean = {
    ramlField.fields.nonEmpty &&
      fields.nonEmpty &&
      fields.contains(ramlField.name)
  }
}

class EnrichmentFilterFactory(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetFields(_, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new EnrichRequestFilter(fields)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )
      case unknownTarget ⇒
        log.warn(s"Annotations 'x-client-ip' and 'x-client-language' are not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}
