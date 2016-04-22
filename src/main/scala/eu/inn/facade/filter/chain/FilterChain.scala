package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(context: FacadeRequestContext, request: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(request, findRequestFilters(request).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def filterResponse(context: FacadeRequestContext, stage: RequestStage, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {

    FutureUtils.chain(response, findResponseFilters(stage, response).map(f ⇒ f.apply(context, _ : FacadeResponse)))
  }

  def filterEvent(context: FacadeRequestContext, stage: RequestStage, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(event, findEventFilters(stage, event).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def findRequestFilters(request: FacadeRequest): Seq[RequestFilter]
  def findResponseFilters(stage: RequestStage, response: FacadeResponse): Seq[ResponseFilter]
  def findEventFilters(stage: RequestStage, event: FacadeRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
