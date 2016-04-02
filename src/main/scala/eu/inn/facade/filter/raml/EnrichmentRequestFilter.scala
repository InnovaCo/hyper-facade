package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.raml.EnrichRequestFilter._
import eu.inn.facade.model.{FacadeRequestContext$, _}
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
      var headers = request.headers
      // todo: iterate over fields recursively in depth when nested fields will be supported
      targetFields.foreach { targetRamlField ⇒
        val annotations = targetRamlField.dataType.annotations
        for ((annotationName, headerName) ← annotationsToHeaders) {
          if (annotations.exists(_.name == annotationName)) {
            headers.get(headerName) match {
              case Some(headerValue :: tail) ⇒
                bodyFields += targetRamlField.name → Text(headerValue)
                headers -= headerName

              case _ ⇒
            }
          }
        }
      }
      FacadeRequest(request.uri, request.method, headers, Obj(bodyFields))
    }
  }
}

object EnrichRequestFilter {
  val annotationsToHeaders = Map(
    Annotation.CLIENT_LANGUAGE → FacadeHeaders.CLIENT_LANGUAGE,
    Annotation.CLIENT_IP → FacadeHeaders.CLIENT_IP
  )
}
