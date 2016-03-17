package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

case class FilterChain(
                         requestFilters: Seq[RequestFilter],
                         responseFilters: Seq[ResponseFilter],
                         eventFilters: Seq[EventFilter]
                       ) {

  def filterRequest(input: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(input, requestFilters.map(f ⇒ f.apply _))
  }

  def filterResponse(input: FacadeRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    FutureUtils.chain(output, responseFilters.map(f ⇒ f.apply(input, _ : FacadeResponse)))
  }

  def filterEvent(input: FacadeRequest, output: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(output, eventFilters.map(f ⇒ f.apply(input, _ : FacadeRequest)))
  }

  def ++ (other: FilterChain): FilterChain = {
    FilterChain(
      requestFilters ++ other.requestFilters,
      responseFilters ++ other.responseFilters,
      eventFilters ++ other.eventFilters
    )
  }
}

object FilterChain {
  val empty = FilterChain(Seq.empty,Seq.empty,Seq.empty)
}
