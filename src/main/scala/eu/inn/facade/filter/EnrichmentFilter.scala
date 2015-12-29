package eu.inn.facade.filter

import eu.inn.facade.filter.model.{Headers, InputFilter, OutputFilter}
import eu.inn.facade.raml.ResourceConfig
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

class EnrichmentFilter(val resourceConfig: ResourceConfig) extends InputFilter with OutputFilter {

  override def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = ???
}
