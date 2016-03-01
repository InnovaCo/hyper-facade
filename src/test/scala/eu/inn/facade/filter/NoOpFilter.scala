package eu.inn.facade.filter

import eu.inn.facade.filter.model.{OutputFilter, TransitionalHeaders, InputFilter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class NoOpFilter extends OutputFilter {
  override def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    Future(headers, body)
  }
}
