package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model.{RamlFilterFactory, RamlTarget}
import eu.inn.facade.filter.parser.PredicateEvaluator

class EmptyFilterFactory extends RamlFilterFactory {
  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    FilterChain.empty
  }

  override def predicateEvaluator: PredicateEvaluator = {
    PredicateEvaluator()
  }
}
