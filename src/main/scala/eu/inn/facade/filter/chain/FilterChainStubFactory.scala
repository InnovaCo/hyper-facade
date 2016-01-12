package eu.inn.facade.filter.chain

class FilterChainStubFactory extends FilterChainFactory {
  override def inputFilterChain(url: String, method: String): FilterChain = {
    FilterChain(Seq())
  }

  override def outputFilterChain(url: String, method: String): FilterChain = {
    FilterChain(Seq())
  }
}
