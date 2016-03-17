package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import scaldi.Injectable


class SimpleFilterChain(
                       initRequestFilters: Seq[RequestFilter] = Seq.empty,
                       initResponseFilters: Seq[ResponseFilter] = Seq.empty,
                       initEventFilters: Seq[EventFilter] = Seq.empty
                       ) extends FilterChain with Injectable {
  def requestFilters(context: RequestFilterContext, request: FacadeRequest): Seq[RequestFilter] = initRequestFilters
  def responseFilters(context: ResponseFilterContext, response: FacadeResponse): Seq[ResponseFilter] = initResponseFilters
  def eventFilters(context: EventFilterContext, event: FacadeRequest): Seq[EventFilter] = initEventFilters
}
