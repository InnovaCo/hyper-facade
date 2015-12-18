package eu.inn.facade.filter.chain

import scala.language.postfixOps

trait FilterChainComponent {

  def inputFilterChain(uri: String, method: String): FilterChain
  def outputFilterChain(uri: String, method: String): FilterChain
}
