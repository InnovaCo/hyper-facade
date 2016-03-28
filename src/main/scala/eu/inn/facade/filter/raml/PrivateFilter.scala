package eu.inn.facade.filter.raml

import eu.inn.binders.dynamic.{Obj, Value}
import eu.inn.facade.model.{EventFilter, EventFilterContext, ResponseFilter, ResponseFilterContext, _}
import eu.inn.facade.filter.raml.PrivateFilter._
import eu.inn.facade.raml.Field
import eu.inn.hyperbus.model.{ErrorBody, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class RequestPrivateFilter(val privateAddresses: PrivateAddresses) extends RequestFilter {
  override def apply(context: RequestFilterContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    if (isAllowedAddress(context.headers, privateAddresses)) Future.successful(request)
    else {
      val error = NotFound(ErrorBody("not_found")) // todo: + messagingContext!!!
      Future.failed(
        new FilterInterruptException(
          FacadeResponse(error),
          message = s"Access to ${request.uri}/${request.method} is restricted"
        )
      )
    }
  }
}

class ResponsePrivateFilter(val privateFields: Seq[Field], val privateAddresses: PrivateAddresses) extends ResponseFilter {
  override def apply(context: ResponseFilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      if (isAllowedAddress(context.requestHeaders, privateAddresses)) response
      else response.copy(
          body = PrivateFilter.filterBody(privateFields, response.body)
        )
    }
  }
}

class EventPrivateFilter(val privateFields: Seq[Field], val privateAddresses: PrivateAddresses) extends EventFilter {
  override def apply(context: EventFilterContext, response: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      if (isAllowedAddress(context.requestHeaders, privateAddresses)) response
      else response.copy(
        body = PrivateFilter.filterBody(privateFields, response.body)
      )
    }
  }
}

object PrivateFilter {
  def filterBody(privateFields: Seq[Field], body: Value): Value = {
    var bodyFields = body.asMap
    privateFields.foreach { field ⇒
      bodyFields -= field.name
    }
    Obj(bodyFields)
  }

  def isAllowedAddress(requestHeaders: Map[String, Seq[String]], privateAddresses: PrivateAddresses): Boolean = {
    requestHeaders.get(FacadeHeaders.CLIENT_ADDRESS) match {
      case None ⇒ false
      case Some(ip :: tail) ⇒ privateAddresses.isAllowedAddress(ip)
    }
  }
}
