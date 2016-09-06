package eu.inn.facade.modules

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import com.typesafe.config.Config
import eu.inn.facade.FacadeConfigPaths
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}

import scala.concurrent.ExecutionContext

class TestHyperbusFactory(val config: Config) {
  lazy val hyperbus = new Hyperbus(newTransportManager(), TestHyperbusFactory.defaultHyperbusGroup(config))(ExecutionContext.fromExecutor(newPoolExecutor()))

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  private def newTransportManager(): TransportManager = {
    new TransportManager(TransportConfigurationLoader.fromConfig(config))
  }
}

object TestHyperbusFactory {
  def defaultHyperbusGroup(config: Config) = {
    if (config.hasPath(FacadeConfigPaths.HYPERBUS_GROUP))
      Some(config.getString(FacadeConfigPaths.HYPERBUS_GROUP))
    else None
  }
}
