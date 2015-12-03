package eu.inn.facade

import eu.inn.facade.events.SubscriptionsManager
import eu.inn.facade.filter.chain.FilterChainStubComponent
import eu.inn.facade.filter.{FilteringDirective, RequestFilterComponent}
import eu.inn.facade.http.{WsRestServiceComponent, StatusMonitorFacade}
import eu.inn.util.{ConsoleIOComponent, ConfigComponent, Logging}
import eu.inn.util.akka.ActorSystemComponent
import eu.inn.util.metrics.StatsComponent

trait ComponentRegistry
  extends WsRestServiceComponent
    with ActorSystemComponent
    with SubscriptionsManager
    with StatsComponent
    with Logging
    with HyperBusComponent
    with ConfigComponent
    with StatusMonitorFacade
    with ConsoleIOComponent
    with FilterChainStubComponent
