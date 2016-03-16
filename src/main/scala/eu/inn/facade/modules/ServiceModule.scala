package eu.inn.facade.modules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.facade.http.{HandleErrorsDirectives, HttpWorker, WsRestServiceApp}
import eu.inn.facade.{HyperBusFactory, StatsReporterFactory}
import eu.inn.hyperbus.HyperBus
import eu.inn.servicecontrol.{ConsoleServiceController, RuntimeShutdownMonitor, StdConsole}
import eu.inn.servicecontrol.api.{Console, Service, ServiceController, ShutdownMonitor}
import scaldi.Module

import scala.concurrent.ExecutionContext

class ServiceModule extends Module {
  bind [HyperBusFactory]        identifiedBy 'hbFactory            to new HyperBusFactory(inject [Config])
  bind [HyperBus]               identifiedBy 'hyperbus             to inject [HyperBusFactory].hyperBus
  bind [StatsReporterFactory]   identifiedBy 'statsFactory         to new StatsReporterFactory(inject [Config])
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("Inn", inject [Config])
  bind [ExecutionContext]       identifiedBy 'executionContext     to inject [ActorSystem].dispatcher
  bind [HandleErrorsDirectives] identifiedBy 'errorsDirectives     to new HandleErrorsDirectives
  bind [HttpWorker]             identifiedBy 'httpWorker           to new HttpWorker
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to new SubscriptionsManager
  bind [Service]                identifiedBy 'restApp              to new WsRestServiceApp
  bind [Console]                identifiedBy 'console              toNonLazy new StdConsole
  bind [ServiceController]      identifiedBy 'serviceController    toNonLazy injected [ConsoleServiceController]
  bind [ShutdownMonitor]        identifiedBy 'shutdownMonitor      toNonLazy injected [RuntimeShutdownMonitor]
}
