package eu.inn.facade.filter.chain

class FilterChainStubComponent extends FilterChainComponent {
  override def inputFilterChain(uri: String, method: String): FilterChain = {
    FilterChain(Seq())
  }

  override def outputFilterChain(uri: String, method: String): FilterChain = {
    FilterChain(Seq())
  }
}
