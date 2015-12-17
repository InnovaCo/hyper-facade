package eu.inn.facade.injectors

import eu.inn.facade.filter.chain.{FilterChainComponent, FilterChainRamlComponent}
import eu.inn.facade.filter.model.Filter
import eu.inn.facade.filter.{NoOpFilter, PrivateResourceFilter}
import scaldi.Module

class FiltersModule extends Module {

  bind [FilterChainComponent] identifiedBy "ramlFilterChain"        to new FilterChainRamlComponent

  bind [Filter]               identifiedBy "noop"                   to new NoOpFilter
  bind [Filter]               identifiedBy "private"                to new PrivateResourceFilter
}
