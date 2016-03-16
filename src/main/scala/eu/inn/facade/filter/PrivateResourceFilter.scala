package eu.inn.facade.filter

import java.io.ByteArrayOutputStream

import eu.inn.binders.dynamic.Text
import eu.inn.facade.model._
import eu.inn.hyperbus.model.{NotFound, DynamicBody, ErrorBody}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class PrivateResourceFilterFactory extends RamlFilterFactory {
  override def createRequestFilter(target: RamlTarget): Option[RequestFilter] = {
    target match {
      case TargetResource(uri) ⇒ Some(new PrivateResourceFilter)
      case TargetMethod(uri, method) ⇒ Some(new PrivateResourceFilter)
      case _ ⇒ None // log warning
    }
  }
  override def createEventFilter(target: RamlTarget): Option[EventFilter] = None
  override def createResponseFilter(target: RamlTarget): Option[ResponseFilter] = None
}


class PrivateResourceFilter extends RequestFilter {
  override def apply(input: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val error = NotFound(ErrorBody("not_found")) // todo: + messagingContext!!!
    Future.failed(
      new FilterInterruptException(
        FacadeResponse(error),
        message = s"Access to ${input.uri}/${input.method} is restricted"
      )
    )
  }
}
