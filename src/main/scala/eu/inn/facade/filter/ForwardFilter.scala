package eu.inn.facade.filter

import java.io.ByteArrayOutputStream

import eu.inn.binders.dynamic.Text
import eu.inn.facade.model.{RamlAwareFilter, InputFilter, TransitionalHeaders}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model.{DynamicBody, ErrorBody}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ForwardFilter(val ramlConfig: RamlConfig) extends RamlAwareFilter {

  override def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    //val traits = ramlConfig.traits(headers.uri.pattern.specific, headers.headers.get())
    val dataStructure = getDataStructure(headers)
    Future.successful {
      (headers, body)
    }
  }

}
