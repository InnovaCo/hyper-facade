package eu.inn.facade

import eu.inn.authentication.AuthenticationService
import eu.inn.facade.filter.NoOpFilterFactory
import eu.inn.facade.filter.model.RamlFilterFactory
import scaldi.Module

class ExtraFiltersModule extends Module {
  bind [RamlFilterFactory]      identifiedBy "paged"         to new NoOpFilterFactory
  bind [AuthenticationService]  identifiedBy 'authService    to injected[AuthenticationService]
}
