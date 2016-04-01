package eu.inn.facade.modules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import eu.inn.facade.{HyperbusFactory, StatsReporterFactory}
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.facade.http.{HttpWorker, WsRestServiceApp}
import eu.inn.hyperbus.Hyperbus
import eu.inn.servicecontrol.api.{Console, Service, ServiceController, ShutdownMonitor}
import eu.inn.servicecontrol.{ConsoleServiceController, RuntimeShutdownMonitor, StdConsole}
import scaldi.Module

import scala.concurrent.ExecutionContext

class ServiceModule extends Module {
  bind [HyperbusFactory]        identifiedBy 'hbFactory            to new HyperbusFactory(inject [Config])
  bind [Hyperbus]               identifiedBy 'hyperbus             to inject [HyperbusFactory].hyperbus
  bind [StatsReporterFactory]   identifiedBy 'statsFactory         to new StatsReporterFactory(inject [Config])
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("facade", inject [Config])
  bind [ExecutionContext]       identifiedBy 'executionContext     to inject [ActorSystem].dispatcher
  bind [HttpWorker]             identifiedBy 'httpWorker           to new HttpWorker
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to new SubscriptionsManager
  bind [Service]                identifiedBy 'restApp              to new WsRestServiceApp
  bind [Console]                identifiedBy 'console              toNonLazy new StdConsole
  bind [ServiceController]      identifiedBy 'serviceController    toNonLazy injected [ConsoleServiceController]
  bind [ShutdownMonitor]        identifiedBy 'shutdownMonitor      toNonLazy injected [RuntimeShutdownMonitor]
}
