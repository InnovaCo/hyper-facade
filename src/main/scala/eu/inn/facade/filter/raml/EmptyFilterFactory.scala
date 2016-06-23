package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model.{RamlFilterFactory, RamlTarget}
import eu.inn.facade.model._

class EmptyFilterFactory extends RamlFilterFactory {
  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    FilterChain.empty
  }
}
