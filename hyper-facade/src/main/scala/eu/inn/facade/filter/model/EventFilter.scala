package eu.inn.facade.filter.model

import eu.inn.facade.model.{FacadeRequest, FacadeRequestContext}

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(context: FacadeRequestContext, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
