package eu.inn.facade.filter

import eu.inn.facade.filter.model.{Headers, Filter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class NoOpFilter extends Filter {
  override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future(requestHeaders, body)
  }
}
