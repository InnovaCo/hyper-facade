package eu.inn.facade.filter.chain

import eu.inn.facade.filter.model.{EventFilter, RequestFilter, ResponseFilter}
import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(contextWithRequest: ContextWithRequest)
                   (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    FutureUtils.chain(contextWithRequest, findRequestFilters(contextWithRequest).map(f ⇒ f.apply(_)))
  }

  def filterResponse(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    FutureUtils.chain(response, findResponseFilters(contextWithRequest.context, response).map(f ⇒ f.apply(contextWithRequest, _ : FacadeResponse)))
  }

  def filterEvent(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(event, findEventFilters(contextWithRequest.context, event).map(f ⇒ f.apply(contextWithRequest, _ : FacadeRequest)))
  }

  def findRequestFilters(contextWithRequest: ContextWithRequest): Seq[RequestFilter]
  def findResponseFilters(context: FacadeRequestContext, response: FacadeResponse): Seq[ResponseFilter]
  def findEventFilters(context: FacadeRequestContext, event: FacadeRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
