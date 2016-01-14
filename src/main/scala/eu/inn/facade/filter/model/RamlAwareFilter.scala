package eu.inn.facade.filter.model

import eu.inn.facade.raml.{DataStructure, RamlConfig}

trait RamlAwareFilter extends Filter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: Headers): Option[DataStructure] = {
    val url = headers.headers(DynamicRequestHeaders.URL)
    val method = headers.headers(DynamicRequestHeaders.METHOD)
    val contentType = headers.headers.get(DynamicRequestHeaders.CONTENT_TYPE)
    if (isInputFilter) ramlConfig.requestDataStructure(url, method, contentType)
    else ramlConfig.responseDataStructure(url, method, headers.statusCode.getOrElse(200))
  }
}
