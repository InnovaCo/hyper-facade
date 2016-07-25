package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model.{MapBackedPredicateEvaluator, PredicateEvaluator, RamlFilterFactory, RamlTarget}

class EmptyFilterFactory extends RamlFilterFactory {
  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    FilterChain.empty
  }

  override def predicateEvaluator: PredicateEvaluator = {
    new MapBackedPredicateEvaluator
  }
}
