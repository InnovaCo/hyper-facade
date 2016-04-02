package eu.inn.facade.model

import eu.inn.facade.filter.RequestContext

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(context: RequestContext, request: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
