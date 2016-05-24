package eu.inn.facade.modules

import eu.inn.facade.filter.chain.{FilterChain, RamlFilterChain, SimpleFilterChain}
import eu.inn.facade.filter.http.{HttpWsRequestFilter, HttpWsResponseFilter, WsEventFilter}
import eu.inn.facade.filter.model.RamlFilterFactory
import eu.inn.facade.filter.raml._
import eu.inn.facade.model._
import scaldi.Module


class FiltersModule extends Module {

  bind [RamlFilterFactory]          identifiedBy "private"                              to injected[PrivateFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "x-client-ip" and "x-client-language"  to injected[EnrichmentFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "rewrite"                              to injected[RewriteRequestFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "feed"                                 to injected[EmptyFilterFactory]

  bind [FilterChain]                identifiedBy "beforeFilterChain"                    to new SimpleFilterChain(
    requestFilters            = Seq(injected[HttpWsRequestFilter])
  )
  bind [FilterChain]                identifiedBy "afterFilterChain"                     to new SimpleFilterChain(
    responseFilters           = Seq(injected[HttpWsResponseFilter]),
    eventFilters              = Seq(injected[WsEventFilter])
  )
  bind [FilterChain]                identifiedBy "ramlFilterChain"                      to injected[RamlFilterChain]
}
