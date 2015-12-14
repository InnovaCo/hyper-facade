package eu.inn.facade.filter.chain

import scala.language.postfixOps

trait FilterChainComponent {

  def filterChain(uri: String): FilterChain
}
