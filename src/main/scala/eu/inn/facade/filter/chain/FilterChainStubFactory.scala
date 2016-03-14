package eu.inn.facade.filter.chain

import eu.inn.hyperbus.transport.api.uri.Uri

class FilterChainStubFactory extends FilterChainFactory {
  override def inputFilterChain(uri: Uri, method: String, contentType: Option[String]): FilterChain = {
    FilterChain(Seq())
  }

  override def outputFilterChain(uri: Uri, method: String): FilterChain = {
    FilterChain(Seq())
  }
}
