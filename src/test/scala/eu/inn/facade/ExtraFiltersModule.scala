package eu.inn.facade

import eu.inn.facade.filter.NoOpFilterFactory
import eu.inn.facade.model.RamlFilterFactory
import scaldi.Module

class ExtraFiltersModule extends Module {
  bind [RamlFilterFactory] identifiedBy "paged" to new NoOpFilterFactory
}
