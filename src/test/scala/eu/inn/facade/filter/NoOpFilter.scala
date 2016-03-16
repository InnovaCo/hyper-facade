package eu.inn.facade.filter

import eu.inn.facade.model.{ResponseFilter, TransitionalHeaders, RequestFilter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class NoOpFilter extends ResponseFilter {
  override def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    Future(headers, body)
  }
}
