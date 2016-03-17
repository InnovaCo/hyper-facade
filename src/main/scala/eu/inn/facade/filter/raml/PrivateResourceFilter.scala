package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.Filters
import eu.inn.facade.model._
import eu.inn.hyperbus.model.{ErrorBody, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class PrivateResourceFilterFactory extends RamlFilterFactory {
  override def createFilters(target: RamlTarget): Filters = {
    target match {
      case TargetResource(_) | TargetMethod(_, _) ⇒ Filters(
        requestFilters = Seq(new PrivateResourceFilter),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )
      case _ ⇒ Filters.empty // log warning
    }
  }
}

class PrivateResourceFilter extends RequestFilter {
  override def apply(context: RequestFilterContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val error = NotFound(ErrorBody("not_found")) // todo: + messagingContext!!!
    Future.failed(
      new FilterInterruptException(
        FacadeResponse(error),
        message = s"Access to ${request.uri}/${request.method} is restricted"
      )
    )
  }
}
