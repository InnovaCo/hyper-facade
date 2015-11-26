package eu.inn.facade.filter

import eu.inn.facade.filter.model.Headers
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

trait InputFilter {

  def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)]
}
