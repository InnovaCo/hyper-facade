package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.Annotation._
import eu.inn.facade.raml.{Annotation, RamlConfig}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrichmentFilter(val ramlConfig: RamlConfig) extends RamlAwareFilter {

  override def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    Future {
      val enrichedBody = enrichBody(headers, body)
      (headers, enrichedBody)
    }
  }

  def enrichBody(headers: TransitionalHeaders, body: DynamicBody): DynamicBody = {
    var enrichedBody = body
    val dataStructure = getDataStructure(headers)
    dataStructure match {
      case Some(structure) ⇒
        structure.body match {
          case Some(httpBody) ⇒
            val fields = httpBody.dataType.fields
            fields.filter(_.dataType.annotations.contains(Annotation(CLIENT_LANGUAGE)))
              .foreach { field ⇒
                val clientLanguage = headers.headerOption("Accept-Language")
                enrichedBody = clientLanguage match {
                  case Some(language) ⇒
                    val bodyFields = body.content.asMap + ((field.name, Text(language)))
                    DynamicBody(Obj(bodyFields))

                  case None ⇒ body
                }
              }
            fields.filter(_.dataType.annotations.contains(Annotation(CLIENT_IP)))
              .foreach { field ⇒
                val clientIp = headers.headerOption("http_x_forwarded_for")
                enrichedBody = clientIp match {
                  case Some(ip) ⇒
                    val bodyFields = body.content.asMap + ((field.name, Text(ip)))
                    DynamicBody(Obj(bodyFields))

                  case None ⇒ body
                }
              }
          case None ⇒
        }
      case None ⇒
    }

    enrichedBody
  }
}
