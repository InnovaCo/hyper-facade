package eu.inn.facade.filter

import eu.inn.binders.dynamic.Obj
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.{DataStructure, RamlConfig}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class PrivateFieldsFilter(val ramlConfig: RamlConfig) extends RamlAwareFilter {

  override def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future {
      getDataStructure(headers) match {
        case Some(structure) ⇒
          val filteredBody = filterBody(body, structure)
          (headers, filteredBody)
        case None ⇒ (headers, body)
      }
    }
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
