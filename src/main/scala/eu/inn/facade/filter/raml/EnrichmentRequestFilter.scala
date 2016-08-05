package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text, Value}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.model._
import eu.inn.facade.raml.{Annotation, Field}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

class EnrichRequestFilter(val field: Field) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val request = contextWithRequest.request
      val enrichedFields = enrichFields(field, request.body.asMap, contextWithRequest.context)
      contextWithRequest.copy(
        request = request.copy(body = Obj(enrichedFields))
      )
    }
  }

  private def enrichFields(ramlField: Field, fields: Map[String, Value], context: FacadeRequestContext): Map[String, Value] = {
      val annotations = ramlField.annotations
      annotations.foldLeft(fields) { (enrichedFields, annotation) ⇒
        annotation.name match {
          case Annotation.CLIENT_IP ⇒
            addField(ramlField.name, Text(context.remoteAddress), fields)

          case Annotation.CLIENT_LANGUAGE ⇒
            context.requestHeaders.get(FacadeHeaders.CLIENT_LANGUAGE) match {
              case Some(value :: _) ⇒
                addField(ramlField.name, Text(value), fields)  // todo: format of header?

              case _ ⇒
                enrichedFields  // do nothing, because header is missing
            }

          case _ ⇒
            enrichedFields// do nothing, this annotation doesn't belong to enrichment filter
        }
      }
  }

  def addField(pathToField: String, value: Value, requestFields: Map[String, Value]): Map[String, Value] = {
    if (pathToField.contains("."))
      pathToField.split('.').toList match {
        case (leadPathSegment :: _) ⇒
          requestFields.get(leadPathSegment) match {
            case Some(subFields) ⇒
              val tailPath = pathToField.substring(leadPathSegment.length + 1)
              requestFields + (leadPathSegment → Obj(addField(tailPath, value, subFields.asMap)))
          }
      }
    else
      requestFields + (pathToField → value)
  }
}

class EnrichmentFilterFactory(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetField(_, field) ⇒
        SimpleFilterChain(
          requestFilters = createRequestFilters(field),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )
      case unknownTarget ⇒
        log.warn(s"Annotations 'x-client-ip' and 'x-client-language' are not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }

  def createRequestFilters(field: Field): Seq[EnrichRequestFilter] = {
    field.annotations.foldLeft(Seq.newBuilder[EnrichRequestFilter]) { (filters, annotation) ⇒
        annotation.name match {
          case Annotation.CLIENT_IP | Annotation.CLIENT_LANGUAGE ⇒
            filters += new EnrichRequestFilter(field)
          case _ ⇒
            filters
        }
    }.result()
  }
}
