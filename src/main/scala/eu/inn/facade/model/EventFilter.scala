package eu.inn.facade.model

import eu.inn.facade.filter.RequestContext

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(context: RequestContext, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
