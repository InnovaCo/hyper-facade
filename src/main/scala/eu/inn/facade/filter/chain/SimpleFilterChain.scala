package eu.inn.facade.filter.chain

import eu.inn.facade.filter.model._
import eu.inn.facade.model._


case class SimpleFilterChain(
                              requestFilters: Seq[RequestFilter] = Seq.empty,
                              responseFilters: Seq[ResponseFilter] = Seq.empty,
                              eventFilters: Seq[EventFilter] = Seq.empty
                       ) extends FilterChain {

  override def findRequestFilters(contextWithRequest: ContextWithRequest): Seq[RequestFilter] = requestFilters
  override def findResponseFilters(context: FacadeRequestContext, response: FacadeResponse): Seq[ResponseFilter] = responseFilters
  override def findEventFilters(context: FacadeRequestContext, event: FacadeRequest): Seq[EventFilter] = eventFilters

  def ++(other: SimpleFilterChain): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters ++ other.requestFilters,
      other.responseFilters ++ responseFilters, // <- reverse order!
      other.eventFilters ++ eventFilters        // <- reverse order!
    )
  }
}
