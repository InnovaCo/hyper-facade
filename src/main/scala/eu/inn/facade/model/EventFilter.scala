package eu.inn.facade.model

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(input: FacadeRequest, output: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
