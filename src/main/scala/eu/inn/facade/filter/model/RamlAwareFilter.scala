package eu.inn.facade.filter.model

import eu.inn.facade.raml.{Method, DataStructure, RamlConfig}

trait RamlAwareFilter extends Filter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: Headers): Option[DataStructure] = {
    val uri = headers.uri.toString
    val method = headers.singleValueHeader(DynamicRequestHeaders.METHOD).getOrElse(Method.GET)
    val contentType = headers.singleValueHeader(DynamicRequestHeaders.CONTENT_TYPE)
    if (isInputFilter) ramlConfig.requestDataStructure(uri, method, contentType)
    else ramlConfig.responseDataStructure(uri, method, headers.statusCode.getOrElse(200))
  }
}
