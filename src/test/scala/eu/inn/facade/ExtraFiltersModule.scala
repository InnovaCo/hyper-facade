package eu.inn.facade

import eu.inn.auth.BasicAuthenticationService
import eu.inn.facade.filter.NoOpFilterFactory
import eu.inn.facade.filter.model.RamlFilterFactory
import scaldi.Module

class ExtraFiltersModule extends Module {
  bind [RamlFilterFactory]           identifiedBy "paged"         to new NoOpFilterFactory
  bind [BasicAuthenticationService]  identifiedBy "authService"   to injected[BasicAuthenticationService]
  bind [TestService]                 identifiedBy "testService"   to injected[TestService]
}
