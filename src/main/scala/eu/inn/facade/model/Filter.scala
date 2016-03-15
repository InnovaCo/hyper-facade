package eu.inn.facade.model

import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

trait Filter {

  def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)]
  def isInputFilter: Boolean = false
  def isOutputFilter: Boolean = false
}
