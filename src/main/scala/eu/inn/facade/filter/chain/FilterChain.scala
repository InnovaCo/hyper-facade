package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(context: RequestFilterContext, request: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(request, requestFilters(context, request).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def filterResponse(context: ResponseFilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    FutureUtils.chain(response, responseFilters(context, response).map(f ⇒ f.apply(context, _ : FacadeResponse)))
  }

  def filterEvent(context: EventFilterContext, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(event, eventFilters(context, event).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def requestFilters(context: RequestFilterContext, request: FacadeRequest): Seq[RequestFilter]
  def responseFilters(context: ResponseFilterContext, response: FacadeResponse): Seq[ResponseFilter]
  def eventFilters(context: EventFilterContext, event: FacadeRequest): Seq[EventFilter]
}
