package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import scaldi.Injectable


case class SimpleFilterChain(
                              requestFilters: Seq[RequestFilter] = Seq.empty,
                              responseFilters: Seq[ResponseFilter] = Seq.empty,
                              eventFilters: Seq[EventFilter] = Seq.empty
                       ) extends FilterChain with Injectable {

  def ++(other: SimpleFilterChain): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters ++ other.requestFilters,
      other.responseFilters ++ responseFilters, // <- reverse order!
      other.eventFilters ++ eventFilters // <- reverse order!
    )
  }

  override def findRequestFilters(request: FacadeRequest): Seq[RequestFilter] = requestFilters

  override def findResponseFilters(context: FacadeRequestContext, response: FacadeResponse): Seq[ResponseFilter] = responseFilters

  override def findEventFilters(context: FacadeRequestContext, event: FacadeRequest): Seq[EventFilter] = eventFilters
}
