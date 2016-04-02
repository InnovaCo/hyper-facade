package eu.inn.facade.model

import eu.inn.facade.filter.RequestContext

import scala.concurrent.{ExecutionContext, Future}


trait ResponseFilter extends Filter {
  def apply(context: RequestContext, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
