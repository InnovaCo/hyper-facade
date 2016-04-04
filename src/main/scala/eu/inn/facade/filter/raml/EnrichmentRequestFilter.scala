package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
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

class EnrichRequestFilter(val targetFields: Seq[Field]) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      var bodyFields = request.body.asMap
      // todo: iterate over fields recursively in depth when nested fields will be supported
      targetFields.foreach { targetRamlField ⇒
        val annotations = targetRamlField.dataType.annotations
        annotations.foreach { annotation ⇒
          annotation.name match {
            case Annotation.CLIENT_IP ⇒
              bodyFields += targetRamlField.name → Text(context.remoteAddress)

            case Annotation.CLIENT_LANGUAGE ⇒
              context.requestHeaders.get(FacadeHeaders.CLIENT_LANGUAGE) match {
                case Some(value :: _) ⇒ bodyFields += targetRamlField.name → Text(value) // todo: format of header?
                case _ ⇒ // do nothing
              }
          }
        }
      }
      request.copy(body = Obj(bodyFields))
    }
  }
}
