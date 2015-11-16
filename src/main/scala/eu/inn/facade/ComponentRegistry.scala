package eu.inn.facade

import eu.inn.facade.events.SubscriptionsManager
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
