package eu.inn.facade.filter.model

import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

trait Filter {

  def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)]
  def isInputFilter: Boolean = false
  def isOutputFilter: Boolean = false
}
