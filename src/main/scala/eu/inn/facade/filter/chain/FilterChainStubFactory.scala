package eu.inn.facade.filter.chain

import eu.inn.hyperbus.transport.api.uri.Uri

class FilterChainStubFactory extends FilterChainFactory {
  override def requestFilterChain(uri: Uri, method: String, contentType: Option[String]): RequestFilterChain = {
    RequestFilterChain(Seq.empty)
  }

  override def outputFilterChain(uri: Uri, method: String): FilterChains = {
    FilterChain(Seq.empty)
  }
}
