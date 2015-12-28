package eu.inn.facade.filter.chain

import scala.language.postfixOps

trait FilterChainFactory {

  def inputFilterChain(uri: String, method: String): FilterChain
  def outputFilterChain(uri: String, method: String): FilterChain
}
