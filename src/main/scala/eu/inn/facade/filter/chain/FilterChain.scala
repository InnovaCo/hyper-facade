package eu.inn.facade.filter.chain


import eu.inn.facade.filter.FilterContext
import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(context: FilterContext, request: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(request, findRequestFilters(context, request).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def filterResponse(context: FilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {

    FutureUtils.chain(response, findResponseFilters(context, response).map(f ⇒ f.apply(context, _ : FacadeResponse)))
  }

  def filterEvent(context: FilterContext, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(event, findEventFilters(context, event).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  // todo: move this metod out of here
  def createFilterContext(originalRequest: FacadeRequest, request: FacadeRequest) = {
    FilterContext(
      originalRequest.uri.pattern.specific,
      originalRequest.method,
      originalRequest.headers,
      request.uri,
      request.method,
      request.headers
    )
  }

  def findRequestFilters(context: FilterContext, request: FacadeRequest): Seq[RequestFilter]
  def findResponseFilters(context: FilterContext, response: FacadeResponse): Seq[ResponseFilter]
  def findEventFilters(context: FilterContext, event: FacadeRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
