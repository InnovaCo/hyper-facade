package eu.inn.facade.modules

import eu.inn.facade.filter.chain.{FilterChain, RamlFilterChain, SimpleFilterChain}
import eu.inn.facade.filter.http.{AuthenticationRequestFilter, HttpWsRequestFilter, HttpWsResponseFilter, WsEventFilter}
import eu.inn.facade.filter.model.RamlFilterFactory
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.filter.raml._
import scaldi.Module


class FiltersModule extends Module {

  bind [RamlFilterFactory]          identifiedBy "deny"                                 to injected[DenyFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "authorize"                            to injected[AuthorizeFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "x-client-ip" and "x-client-language"  to injected[EnrichmentFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "rewrite"                              to injected[RewriteFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "embed"                                to injected[EmbedFilterFactory]

  bind [FilterChain]                identifiedBy "beforeFilterChain"                    to SimpleFilterChain(
    requestFilters            = Seq(injected[HttpWsRequestFilter],
                                    injected[AuthenticationRequestFilter])
  )
  bind [FilterChain]                identifiedBy "afterFilterChain"                     to SimpleFilterChain(
    responseFilters           = Seq(injected[HttpWsResponseFilter]),
    eventFilters              = Seq(injected[WsEventFilter])
  )
  bind [FilterChain]                identifiedBy "ramlFilterChain"                      to injected[RamlFilterChain]
  bind [PredicateEvaluator]         identifiedBy "predicateEvaluator"                   to PredicateEvaluator()
}
