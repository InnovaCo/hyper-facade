package eu.inn.facade.filter.chain

import scala.language.postfixOps

trait FilterChainFactory {

  def inputFilterChain(url: String, method: String, contentType: Option[String]): FilterChain
  def outputFilterChain(url: String, method: String): FilterChain
}
