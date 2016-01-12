package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.Annotation._
import eu.inn.facade.raml.{Annotation, RamlConfig}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class EnrichmentFilter(val ramlConfig: RamlConfig) extends RamlAwareFilter {

  override def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future {
      val enrichedBody = enrichBody(headers, body)
      (headers, enrichedBody)
    }
  }

  def enrichBody(headers: Headers, body: DynamicBody): DynamicBody = {
    var enrichedBody = body
    val dataStructure = getDataStructure(headers)
    dataStructure match {
      case Some(structure) ⇒
        val fields = structure.body.fields
        fields.filter(_.annotations.contains(Annotation(CLIENT_LANGUAGE)))
          .foreach { field ⇒
            val clientLanguage = headers.headers.get("Accept-Language")
            enrichedBody = clientLanguage match {
              case Some(language) ⇒ {
                val bodyFields = body.content.asMap + ((field.name, Text(language)))
                DynamicBody(Obj(bodyFields))
              }
              case None ⇒ body
            }
          }
        fields.filter(_.annotations.contains(Annotation(CLIENT_IP)))
          .foreach { field ⇒
            val clientIp = headers.headers.get("http_x_forwarded_for")
            enrichedBody = clientIp match {
              case Some(ip) ⇒ {
                val bodyFields = body.content.asMap + ((field.name, Text(ip)))
                DynamicBody(Obj(bodyFields))
              }
              case None ⇒ body
            }
          }
      case None ⇒
    }

    enrichedBody
  }
}
