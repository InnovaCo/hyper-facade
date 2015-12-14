package eu.inn.facade.filter.chain

trait FilterChainStubComponent extends FilterChainComponent {
  override def filterChain(uri: String): FilterChain = {
    FilterChain(Seq(), Seq())
  }
}
