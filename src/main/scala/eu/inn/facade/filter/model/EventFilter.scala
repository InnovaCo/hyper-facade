package eu.inn.facade.filter.model

import eu.inn.facade.model.{ContextWithRequest, FacadeRequest}

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
