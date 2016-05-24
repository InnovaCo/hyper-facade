package eu.inn.facade.filter.model

import eu.inn.facade.model.{FacadeRequestContext, FacadeResponse}

import scala.concurrent.{ExecutionContext, Future}


trait ResponseFilter extends Filter {
  def apply(context: FacadeRequestContext, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
