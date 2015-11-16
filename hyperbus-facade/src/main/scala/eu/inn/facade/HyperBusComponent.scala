package eu.inn.facade

import java.util.concurrent.{SynchronousQueue, TimeUnit, ThreadPoolExecutor, Executor}

import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import eu.inn.util.ConfigComponent

import scala.concurrent.ExecutionContext

trait HyperBusComponent {
  this: ConfigComponent ⇒
  lazy val hyperBus = new HyperBus(newTransportManager())(ExecutionContext.fromExecutor(newPoolExecutor()))

  protected def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5*60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  protected def newTransportManager(): TransportManager = {
    new TransportManager(TransportConfigurationLoader.fromConfig(config))
  }
}
