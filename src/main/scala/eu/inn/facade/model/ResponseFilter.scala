package eu.inn.facade.model

import scala.concurrent.{ExecutionContext, Future}

trait ResponseFilter extends Filter {
  def apply(input: FacadeRequest, output: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
