package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Text, Obj}
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.{Field, DataStructure, RamlConfig}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class EnrichmentFilter(val ramlConfig: RamlConfig) extends Filter {
  val LANGUAGE_FIELD = "language"
  val CLIENT_IP_FIELD = "clientIp"

  def getDataStructure(headers: Headers): DataStructure

  override def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future {
      val enrichedHeaders = enrichHeaders(headers)
      val filteredBody = filterBody(body, getDataStructure(headers))
      val enrichedBody = enrichBody(enrichedHeaders, filteredBody)
      (enrichedHeaders, enrichedBody)
    }
  }

  // TODO: sort out if headers should be enriched
  def enrichHeaders(headers: Headers): Headers = {
    headers
  }

  def enrichBody(headers: Headers, body: DynamicBody): DynamicBody = {
    var enrichedBody = body
    val fields = getDataStructure(headers).body.fields
    if (fields.contains(Field(LANGUAGE_FIELD))) {
      val clientLanguage = headers.headers.get("Accept-Language")
      enrichedBody = clientLanguage match {
        case Some(language) ⇒ {
          val bodyFields = body.content.asMap + ((LANGUAGE_FIELD, Text(language)))
          DynamicBody(Obj(bodyFields))
        }
        case None ⇒ body
      }
    }
    if (fields.contains(Field(CLIENT_IP_FIELD))) {
      val clientIp = headers.headers.get("http_x_forwarded_for")
      enrichedBody = clientIp match {
        case Some(ip) ⇒ {
          val bodyFields = body.content.asMap + ((CLIENT_IP_FIELD, Text(ip)))
          DynamicBody(Obj(bodyFields))
        }
        case None ⇒ body
      }
    }
    enrichedBody
  }

  def filterBody(body: DynamicBody, dataStructure: DataStructure): DynamicBody = {
    val privateFieldNames = dataStructure.body.fields.foldLeft(Seq[String]()) { (privateFields, field) ⇒
      if (field.isPrivate) privateFields :+ field.name
      else privateFields
    }
    var bodyFields = body.content.asMap
    privateFieldNames.foreach { fieldName ⇒
      bodyFields -= fieldName
    }
    DynamicBody(Obj(bodyFields))
  }
}
