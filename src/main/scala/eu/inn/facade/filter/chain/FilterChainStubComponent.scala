package eu.inn.facade.filter.chain

trait FilterChainStubComponent extends FilterChainComponent {
  override def filterChain(url: String): FilterChain = {
    FilterChain(Seq(), Seq())
  }
}
