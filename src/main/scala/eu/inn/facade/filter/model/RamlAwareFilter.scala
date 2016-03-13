package eu.inn.facade.filter.model

import eu.inn.facade.raml.{DataStructure, Method, RamlConfig}
import eu.inn.hyperbus.model.Header

trait RamlAwareFilter extends Filter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: TransitionalHeaders): Option[DataStructure] = {
    val uri = headers.uri.toString
    val method = headers.headerOption(Header.METHOD).getOrElse(Method.GET)
    val contentType = headers.headerOption(Header.CONTENT_TYPE)
    if (isInputFilter) ramlConfig.requestDataStructure(uri, method, contentType)
    else ramlConfig.responseDataStructure(uri, method, headers.statusCode.getOrElse(200))
  }
}
