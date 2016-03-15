package eu.inn.facade.model

import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

trait OutputFilter extends Filter {
  def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)]
}
