package eu.inn.facade.filter.chain

import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait FilterChainFactory {
  def requestFilterChain(input: FacadeRequest): RequestFilterChain
  def responseFilterChain(input: FacadeRequest, output: FacadeResponse): ResponseFilterChain
  def eventFilterChain(input: FacadeRequest, output: FacadeRequest): EventFilterChain
}

class FilterChains(chainFactory: FilterChainFactory) {

  def filterRequest(input: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    chainFactory.requestFilterChain(input).applyFilters(input)
  }

  def filterResponse(input: FacadeRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    chainFactory.responseFilterChain(input, output).applyFilters(input, output)
  }

  def filterEvent(input: FacadeRequest, output: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    chainFactory.eventFilterChain(input, output).applyFilters(input, output)
  }
}