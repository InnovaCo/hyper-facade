package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps


case class ResponseFilterChain(filters: Seq[ResponseFilter]) {
  def applyFilters(input: FacadeRequest, output: FacadeResponse)
                  (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    FutureUtils.chain(output, filters.map(f ⇒ f.apply(input, _ : FacadeResponse)))
  }
}


object ResponseFilterChain {
  val empty = ResponseFilterChain(Seq.empty)
}