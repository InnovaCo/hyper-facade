package eu.inn.facade.filter.model

import eu.inn.facade.model.{FacadeRequest, FacadeRequestContext}

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(context: FacadeRequestContext, request: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
