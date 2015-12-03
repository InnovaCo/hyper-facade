package eu.inn.facade.filter.chain

import scala.language.postfixOps

trait FilterChainComponent {

  def filterChain(url: String): FilterChain
}
