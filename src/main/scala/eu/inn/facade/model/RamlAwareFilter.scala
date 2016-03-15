package eu.inn.facade.model

import eu.inn.facade.raml.{DataStructure, Method, RamlConfig}
import eu.inn.hyperbus.model.Header

trait RamlAwareInputFilter extends InputFilter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: TransitionalHeaders): Option[DataStructure] = {
    val uri = headers.uri.toString
    val method = headers.headerOption(Header.METHOD).getOrElse(Method.GET)
    val contentType = headers.headerOption(Header.CONTENT_TYPE)
    ramlConfig.requestDataStructure(uri, method, contentType)
  }
}

trait RamlAwareOutputFilter extends OutputFilter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: TransitionalHeaders): Option[DataStructure] = {
    val uri = headers.uri.toString
    val method = headers.headerOption(Header.METHOD).getOrElse(Method.GET)
    val contentType = headers.headerOption(Header.CONTENT_TYPE)
    ramlConfig.responseDataStructure(uri, method, headers.statusCode.getOrElse(200))
  }
}
