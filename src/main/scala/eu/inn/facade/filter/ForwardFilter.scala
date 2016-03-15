package eu.inn.facade.filter

import eu.inn.facade.model.{InputFilter, TransitionalHeaders}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future

class ForwardFilter(val ramlConfig: RamlConfig) extends InputFilter {

  override def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    //val traits = ramlConfig.traits(headers.uri.pattern.specific, headers.headers.get())
    //val dataStructure = getDataStructure(headers)
    Future.successful {
      (headers, body)
    }
  }

}
