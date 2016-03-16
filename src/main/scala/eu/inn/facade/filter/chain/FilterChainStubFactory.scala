package eu.inn.facade.filter.chain

import eu.inn.facade.model.{FacadeResponse, FacadeRequest}

class FilterChainStubFactory extends FilterChainFactory {
  override def requestFilterChain(input: FacadeRequest): RequestFilterChain = RequestFilterChain.empty
  override def eventFilterChain(input: FacadeRequest, output: FacadeRequest): EventFilterChain = EventFilterChain.empty
  override def responseFilterChain(input: FacadeRequest, output: FacadeResponse): ResponseFilterChain = ResponseFilterChain.empty
}
