package eu.inn.facade.model

import eu.inn.facade.filter.FilterContext

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(context: FilterContext, request: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
