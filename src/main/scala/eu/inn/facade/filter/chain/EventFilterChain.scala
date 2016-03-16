package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class EventFilterChain(filters: Seq[EventFilter]) {
  def applyFilters(input: FacadeRequest, output: FacadeRequest)
                  (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    FutureUtils.chain(output, filters.map(f ⇒ f.apply(input, _)))
  }
}

object EventFilterChain {
  val empty = EventFilterChain(Seq.empty)
}