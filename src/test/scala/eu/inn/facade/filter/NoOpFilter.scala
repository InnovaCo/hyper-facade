package eu.inn.facade.filter

import eu.inn.facade.filter.model.{OutputFilter, Headers, InputFilter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class NoOpFilter extends OutputFilter {
  override def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future(headers, body)
  }
}
