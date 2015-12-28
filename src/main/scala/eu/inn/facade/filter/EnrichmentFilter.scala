package eu.inn.facade.filter

import eu.inn.facade.filter.model.{Headers, OutputFilter, InputFilter}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

class EnrichmentFilter(val ramlConfig: RamlConfig) extends InputFilter with OutputFilter {

  override def apply(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = ???
}
