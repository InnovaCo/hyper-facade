package eu.inn.facade.model

import eu.inn.facade.filter.FilterContext

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(context: FilterContext, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
