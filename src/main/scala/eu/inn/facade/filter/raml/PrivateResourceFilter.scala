package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import eu.inn.hyperbus.model.{ErrorBody, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class PrivateResourceFilterFactory extends RamlFilterFactory {
  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetResource(_,_) | TargetMethod(_, _, _) ⇒ SimpleFilterChain(
        requestFilters = Seq(new PrivateResourceFilter),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )
      case _ ⇒ FilterChain.empty // log warning
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
