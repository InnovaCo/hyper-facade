package eu.inn.facade

import ch.qos.logback.classic.{Level, Logger}
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.facade.raml.{RamlConfig, RamlConfigParser}
import org.slf4j.LoggerFactory
import scaldi.Injector

import scala.collection.JavaConversions._

class ConfigsFactory {

  def ramlConfig(appConfig: Config)(implicit inj: Injector): RamlConfig = {
    val factory = new JavaNodeFactory
    val ramlConfigPath = ramlFilePath(appConfig)
    RamlConfigParser(factory.createApi(ramlConfigPath)).parseRaml
  }

  private def ramlFilePath(config: Config): String = {
    val filePath = config.getString(FacadeConfig.RAML_FILE)

    // it means that config contains absolute file path
    if (filePath.startsWith("/")) filePath
    // otherwise treat it as relative file path
    else Thread.currentThread().getContextClassLoader.getResource(filePath).getFile
  }
}

object FacadeConfig {
  val ROOT = "hyperbus-facade."
  val PRIVATE_ADDRESSES = ROOT + "private.addresses"
  val PRIVATE_NETWORKS = ROOT + "private.networks"
  val LOGGERS = ROOT + "loggers"
  val RAML_FILE = ROOT + "raml-file"
  val HYPERBUS_GROUP = ROOT + "hyperbus.group-name"
  val GRAPHITE = ROOT + "graphite"
  val FILTERS = ROOT + "filters"
  val HTTP = ROOT + "http-transport"
  val SHUTDOWN_TIMEOUT = ROOT + "shutdown-timeout"
  val MAX_RESUBSCRIPTIONS = ROOT + "max-resubscriptions"
}
