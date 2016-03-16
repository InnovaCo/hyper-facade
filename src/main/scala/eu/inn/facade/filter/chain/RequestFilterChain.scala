package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class RequestFilterChain(filters: Seq[RequestFilter]) {
  def applyFilters(input: FacadeRequest)
                  (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(input, filters.map(f ⇒ f.apply _))
  }
}

object RequestFilterChain {
  val empty = RequestFilterChain(Seq.empty)
}