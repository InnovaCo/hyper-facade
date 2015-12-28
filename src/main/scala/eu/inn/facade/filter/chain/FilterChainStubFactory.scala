package eu.inn.facade.filter.chain

class FilterChainStubFactory extends FilterChainFactory {
  override def inputFilterChain(uri: String, method: String): FilterChain = {
    FilterChain(Seq())
  }

  override def outputFilterChain(uri: String, method: String): FilterChain = {
    FilterChain(Seq())
  }
}
