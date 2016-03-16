package eu.inn.facade.model

import scala.concurrent.{ExecutionContext, Future}

trait RequestFilter extends Filter {
  def apply(input: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
