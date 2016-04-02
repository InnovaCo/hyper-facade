package eu.inn.facade.model

import scala.concurrent.{ExecutionContext, Future}


trait ResponseFilter extends Filter {
  def apply(context: FacadeRequestContext, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
