package eu.inn.facade.injectors

import eu.inn.facade.filter.chain.{FilterChainComponent, FilterChainRamlComponent}
import eu.inn.facade.filter.model.Filter
import eu.inn.facade.filter.{NoOpFilter, PrivateResourceFilter}
import scaldi.Module

// review: это дефолтные фильтры? Нужна возможность подключить внешние фильтры (jar?)
class FiltersModule extends Module {

  bind [Filter]               identifiedBy "noop"              to new NoOpFilter
  bind [Filter]               identifiedBy "private"           to new PrivateResourceFilter

  bind [FilterChainComponent] identifiedBy "ramlFilterChain"   to new FilterChainRamlComponent
}
