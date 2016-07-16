package eu.inn.facade.model

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(context: FacadeRequestContext, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}