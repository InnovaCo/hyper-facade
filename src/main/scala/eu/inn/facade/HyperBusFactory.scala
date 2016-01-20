package eu.inn.facade

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import com.typesafe.config.Config
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}

import scala.concurrent.ExecutionContext

class HyperBusFactory(val config: Config) {

  lazy val hyperBus = new HyperBus(newTransportManager(), HyperBusFactory.defaultHyperBusGroup(config))(ExecutionContext.fromExecutor(newPoolExecutor()))

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  private def newTransportManager(): TransportManager = {
    new TransportManager(TransportConfigurationLoader.fromConfig(config))
  }
}

object HyperBusFactory {
  val HYPERBUS_GROUP_NAME = "hyperbus.transports.kafka-server.defaultGroupName"

  def defaultHyperBusGroup(config: Config) = {
    if (config.hasPath(HyperBusFactory.HYPERBUS_GROUP_NAME))
      Some(config.getString(HyperBusFactory.HYPERBUS_GROUP_NAME))
    else None
  }
}
