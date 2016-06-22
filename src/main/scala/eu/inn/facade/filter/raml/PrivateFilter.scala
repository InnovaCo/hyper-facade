package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Value}
import eu.inn.facade.filter.model.{EventFilter, RequestFilter, ResponseFilter}
import eu.inn.facade.filter.raml.PrivateFilter._
import eu.inn.facade.model._
import eu.inn.facade.model.ContextStorage._
import eu.inn.facade.raml.annotationtypes.private_
import eu.inn.facade.raml.{Annotation, Field}
import eu.inn.hyperbus.model.{ErrorBody, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class RequestPrivateFilter(val privateAddresses: PrivateAddresses, val privateArgs: private_) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val context = contextWithRequest.context
      val accessAllowed = context.authUser match {
        case Some(authUser) ⇒
          privateArgs.getAllowedUsers.contains(authUser.id) ||
            privateArgs.getAllowedRoles.toSet.intersect(authUser.roles).nonEmpty ||
            isAllowedAddress(context.requestHeaders, privateAddresses, privateArgs.getArePrivateNetworksAllowed())
        case None ⇒
          false
      }
      if (accessAllowed)
        contextWithRequest
      else {
        val request = contextWithRequest.request
        val error = NotFound(ErrorBody("not-found")) // todo: + messagingContext!!!
        throw new FilterInterruptException(
          FacadeResponse(error),
          message = s"Access to ${request.uri}/${request.method} is restricted"
        )
      }
    }
  }
}

class ResponsePrivateFilter(val fields: Seq[Field], val privateAddresses: PrivateAddresses) extends ResponseFilter {
  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.copy(
        body = PrivateFilter.filterBody(fields, response.body, contextWithRequest, privateAddresses)
      )
    }
  }
}

class EventPrivateFilter(val fields: Seq[Field], val privateAddresses: PrivateAddresses) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, response: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      response.copy(
        body = PrivateFilter.filterBody(fields, response.body, contextWithRequest, privateAddresses)
      )
    }
  }
}

object PrivateFilter {

  def filterBody(fields: Seq[Field], body: Value, contextWithRequest: ContextWithRequest, privateAddresses: PrivateAddresses): Value = {
    body match {
      case obj: Obj ⇒
        val filteredFields = filterFields(fields, body.asMap, contextWithRequest, privateAddresses)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  def filterFields(ramlFields: Seq[Field], fields: scala.collection.Map[String, Value], contextWithRequest: ContextWithRequest, privateAddresses: PrivateAddresses): scala.collection.Map[String, Value] = {
    ramlFields.foldLeft(fields) { (nonPrivateFields, ramlField) ⇒
      if (isPrivate(ramlField, contextWithRequest, privateAddresses))
        nonPrivateFields - ramlField.name
      else {
        if (shouldFilterNestedFields(ramlField, nonPrivateFields)) {
          val fieldName = ramlField.name
          val nestedFields = nonPrivateFields(fieldName).asMap
          val filteredNestedFields = filterFields(ramlField.fields, nestedFields, contextWithRequest, privateAddresses)
          nonPrivateFields + (ramlField.name → Obj(filteredNestedFields))
        } else
          nonPrivateFields
      }
    }
  }

  def isPrivate(field: Field, contextWithRequest: ContextWithRequest, privateAddresses: PrivateAddresses): Boolean = {
    field.annotations.find(_.name == Annotation.PRIVATE) match {
      case Some(Annotation(_, Some(privateArgs: private_))) ⇒
        contextWithRequest.context.authUser match {
          case Some(authUser) ⇒
            privateArgs.getAllowedUsers.contains(authUser.id) ||
              privateArgs.getAllowedRoles.toSet.intersect(authUser.roles).nonEmpty ||
              isAllowedAddress(contextWithRequest.context.requestHeaders, privateAddresses, privateArgs.getArePrivateNetworksAllowed())
          case None ⇒
            true
        }

      case None ⇒
        false
    }
  }

  def shouldFilterNestedFields(ramlField: Field, fields: scala.collection.Map[String, Value]): Boolean = {
    ramlField.fields.nonEmpty &&
      fields.nonEmpty &&
      fields.contains(ramlField.name)
  }

  def isAllowedAddress(requestHeaders: Map[String, Seq[String]], privateAddresses: PrivateAddresses, privateNetworksAllowed: Boolean): Boolean = {
    privateNetworksAllowed && (
      requestHeaders.get(FacadeHeaders.CLIENT_IP) match {
        case Some(ip :: tail) ⇒ privateAddresses.isAllowedAddress(ip)
        case _ ⇒ false
      })
  }
}
