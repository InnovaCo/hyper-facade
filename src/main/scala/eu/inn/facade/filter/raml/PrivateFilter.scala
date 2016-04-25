package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Value}
import eu.inn.facade.filter.raml.PrivateFilter._
import eu.inn.facade.model.{EventFilter, ResponseFilter, _}
import eu.inn.facade.raml.{Annotation, Field}
import eu.inn.hyperbus.model.{ErrorBody, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class RequestPrivateFilter(val privateAddresses: PrivateAddresses) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    if (isAllowedAddress(context.requestHeaders, privateAddresses)) Future.successful(request)
    else {
      val error = NotFound(ErrorBody("not-found")) // todo: + messagingContext!!!
      Future.failed(
        new FilterInterruptException(
          FacadeResponse(error),
          message = s"Access to ${request.uri}/${request.method} is restricted"
        )
      )
    }
  }
}

class ResponsePrivateFilter(val fields: Seq[Field], val privateAddresses: PrivateAddresses) extends ResponseFilter {
  override def apply(context: FacadeRequestContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      if (isAllowedAddress(context.requestHeaders, privateAddresses)) response
      else response.copy(
          body = PrivateFilter.filterBody(fields, response.body)
        )
    }
  }
}

class EventPrivateFilter(val fields: Seq[Field], val privateAddresses: PrivateAddresses) extends EventFilter {
  override def apply(context: FacadeRequestContext, response: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      if (isAllowedAddress(context.requestHeaders, privateAddresses)) {
        response
      }
      else {
        response.copy(
          body = PrivateFilter.filterBody(fields, response.body)
        )
      }
    }
  }
}

object PrivateFilter {

  def filterBody(fields: Seq[Field], body: Value): Value = {
    body match {
      case obj: Obj ⇒
        val filteredFields = filterFields(fields, body.asMap)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  def filterFields(ramlFields: Seq[Field], fields: scala.collection.Map[String, Value]): scala.collection.Map[String, Value] = {
    ramlFields.foldLeft(fields) { (nonPrivateFields, ramlField) ⇒
      if (isPrivate(ramlField))
        nonPrivateFields - ramlField.name
      else {
        if (shouldFilterNestedFields(ramlField, nonPrivateFields)) {
          val fieldName = ramlField.name
          val nestedFields = nonPrivateFields(fieldName).asMap
          val filteredNestedFields = filterFields(ramlField.fields, nestedFields)
          nonPrivateFields + (ramlField.name → Obj(filteredNestedFields))
        } else
          nonPrivateFields
      }
    }
  }

  def isPrivate(field: Field): Boolean = {
    field.annotations.exists(_.name == Annotation.PRIVATE)
  }

  def shouldFilterNestedFields(ramlField: Field, fields: scala.collection.Map[String, Value]): Boolean = {
    ramlField.fields.nonEmpty &&
      fields.nonEmpty &&
      fields.contains(ramlField.name)
  }

  def isAllowedAddress(requestHeaders: Map[String, Seq[String]], privateAddresses: PrivateAddresses): Boolean = {
    requestHeaders.get(FacadeHeaders.CLIENT_IP) match {
      case Some(ip :: tail) ⇒ privateAddresses.isAllowedAddress(ip)
      case _ ⇒ false
    }
  }
}
