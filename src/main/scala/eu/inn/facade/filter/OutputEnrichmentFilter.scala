package eu.inn.facade.filter

import eu.inn.facade.filter.model.{DynamicRequestHeaders, Headers, OutputFilter}
import eu.inn.facade.raml.{DataStructure, RamlConfig}

class OutputEnrichmentFilter(ramlConfig: RamlConfig) extends EnrichmentFilter(ramlConfig) with OutputFilter {

  override def getDataStructure(headers: Headers): DataStructure = {
    val url = headers.headers(DynamicRequestHeaders.URL)
    val method = headers.headers(DynamicRequestHeaders.METHOD)
    val statusCode = headers.statusCode.getOrElse(200)
    val dataStructure = ramlConfig.responseDataStructure(url, method, statusCode)
    dataStructure
  }
}
