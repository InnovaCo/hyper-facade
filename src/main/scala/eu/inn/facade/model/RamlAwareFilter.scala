package eu.inn.facade.model

import eu.inn.facade.raml.{DataStructure, Method, RamlConfig}
import eu.inn.hyperbus.model.Header

trait RamlAwareInputFilter extends RequestFilter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: TransitionalHeaders): Option[DataStructure] = {
    val uri = headers.uri.toString
    val method = headers.headerOption(Header.METHOD).getOrElse(Method.GET)
    val contentType = headers.headerOption(Header.CONTENT_TYPE)
    ramlConfig.requestDataStructure(uri, method, contentType)
  }
}
