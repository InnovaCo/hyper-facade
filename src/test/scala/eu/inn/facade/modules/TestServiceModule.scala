package eu.inn.facade.modules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.facade.workers.{HttpWorker, TestWsRestServiceApp}
import eu.inn.hyperbus.Hyperbus
import eu.inn.servicecontrol.api.Service
import scaldi.Module

import scala.concurrent.ExecutionContext

/**
  * This class is needed to inject TestWsRestServiceApp - modified version of WsRestServiceApp without a shutdown hook.
  * Since test suites run one-by-one, we don't need to multiple shutdown hooks triggers on the end of a test run
  */
class TestServiceModule extends Module {
  bind [TestHyperbusFactory]    identifiedBy 'hbFactory            toProvider new TestHyperbusFactory(inject [Config])
  bind [Hyperbus]               identifiedBy 'hyperbus             to inject [TestHyperbusFactory].hyperbus
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("facade", inject [Config])
  bind [ExecutionContext]       identifiedBy 'executionContext     to inject[ActorSystem].dispatcher
  bind [HttpWorker]             identifiedBy 'httpWorker           to injected[HttpWorker]
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to injected[SubscriptionsManager]
  bind [Service]                identifiedBy 'restApp              to injected[TestWsRestServiceApp]
}
