package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

case class Filters(
                         requestFilters: Seq[RequestFilter],
                         responseFilters: Seq[ResponseFilter],
                         eventFilters: Seq[EventFilter]
                       ) {
  def ++ (other: Filters): Filters = {
    Filters(
      requestFilters ++ other.requestFilters,
      responseFilters ++ other.responseFilters,
      eventFilters ++ other.eventFilters
    )
  }
}

object Filters {
  val empty = Filters(Seq.empty,Seq.empty,Seq.empty)
}
