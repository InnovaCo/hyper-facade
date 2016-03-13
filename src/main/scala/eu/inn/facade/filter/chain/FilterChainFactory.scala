package eu.inn.facade.filter.chain

import eu.inn.hyperbus.transport.api.uri.Uri

import scala.language.postfixOps

trait FilterChainFactory {

  def inputFilterChain(uri: Uri, method: String, contentType: Option[String]): FilterChain
  def outputFilterChain(uri: Uri, method: String): FilterChain
}
