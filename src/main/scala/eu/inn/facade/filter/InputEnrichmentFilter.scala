package eu.inn.facade.filter

import eu.inn.facade.filter.model.{DynamicRequestHeaders, InputFilter, Headers}
import eu.inn.facade.raml.{DataStructure, RamlConfig}

class InputEnrichmentFilter(ramlConfig: RamlConfig) extends EnrichmentFilter(ramlConfig) with InputFilter {

  override def getDataStructure(headers: Headers): DataStructure = {
    val url = headers.headers(DynamicRequestHeaders.URL)
    val method = headers.headers(DynamicRequestHeaders.METHOD)
    val dataStructure = ramlConfig.requestDataStructure(url, method)
    dataStructure
  }
}
