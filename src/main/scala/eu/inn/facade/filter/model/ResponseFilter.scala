package eu.inn.facade.filter.model

import eu.inn.facade.model.{ContextWithRequest, FacadeResponse}

import scala.concurrent.{ExecutionContext, Future}

trait ResponseFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
