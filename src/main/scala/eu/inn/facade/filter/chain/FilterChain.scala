package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(context: FacadeRequestContext, request: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(request, findRequestFilters(request).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def filterResponse(context: FacadeRequestContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {

    FutureUtils.chain(response, findResponseFilters(context.prepared.head, response).map(f ⇒ f.apply(context, _ : FacadeResponse)))
  }

  def filterEvent(context: FacadeRequestContext, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(event, findEventFilters(context.prepared.head, event).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def findRequestFilters(request: FacadeRequest): Seq[RequestFilter]
  def findResponseFilters(preparedRequestContext: PreparedRequestContext, response: FacadeResponse): Seq[ResponseFilter]
  def findEventFilters(preparedRequestContext: PreparedRequestContext, event: FacadeRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
