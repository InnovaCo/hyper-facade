package eu.inn.facade.filter

import eu.inn.facade.filter.model.{Headers, InputFilter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NoOpFilter extends InputFilter {
  override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future(requestHeaders, body)
  }
}
