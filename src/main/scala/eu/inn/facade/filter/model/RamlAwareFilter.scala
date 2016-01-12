package eu.inn.facade.filter.model

import eu.inn.facade.raml.{DataStructure, RamlConfig}

trait RamlAwareFilter extends Filter {
  def ramlConfig: RamlConfig

  def getDataStructure(headers: Headers): Option[DataStructure] = {
    val url = headers.headers(DynamicRequestHeaders.URL)
    val method = headers.headers(DynamicRequestHeaders.METHOD)
    if (isInputFilter) ramlConfig.requestDataStructure(url, method)
    else ramlConfig.responseDataStructure(url, method, headers.statusCode.getOrElse(200))
  }
}
