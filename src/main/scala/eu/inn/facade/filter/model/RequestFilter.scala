package eu.inn.facade.filter.model

import eu.inn.facade.model.{ContextWithRequest, FacadeRequest, FacadeRequestContext}

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest)
           (implicit ec: ExecutionContext): Future[ContextWithRequest]
}
