package eu.inn.facade.model

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(context: FacadeRequestContext, request: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
