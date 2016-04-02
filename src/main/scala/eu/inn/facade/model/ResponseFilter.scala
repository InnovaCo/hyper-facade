package eu.inn.facade.model

import eu.inn.facade.filter.FilterContext

import scala.concurrent.{ExecutionContext, Future}


trait ResponseFilter extends Filter {
  def apply(context: FilterContext, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
