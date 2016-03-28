package eu.inn.facade

import ch.qos.logback.classic.{Level, Logger}
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.facade.raml.{RamlConfig, RamlConfigParser}
import org.slf4j.LoggerFactory
import scaldi.Injector

import scala.collection.JavaConversions._

class ConfigsFactory {

  /**
    * Parse and merge local configs specified in system property -Dconfig.localfile
    * You can specify multiple files, separated by semicolon
    *
    * For example:
    * -Dconfig.localfile=/etc/global-inn.conf;/home/app/my-local-priority.conf
    */
  def config: Config = {
    val conf = System.getProperty("config.localfile", "")
      .split(';')
      .foldLeft(ConfigFactory.load())({ (conf, filePath) ⇒
        val file = new java.io.File(filePath.trim)

        if (file.canRead) {
          ConfigFactory.parseFile(file).withFallback(conf)
        } else {
          conf
        }
      })
      .resolve()

    /**
      * Reconfigure log level
      */
    if (conf.hasPath(FacadeConfig.LOGGERS)) {
      conf.getObject(FacadeConfig.LOGGERS).toMap foreach { case (name, obj) ⇒
        val level = obj.atPath("/").getString("/")

        LoggerFactory.getLogger(name) match {
          case l: Logger ⇒ l.setLevel(Level.toLevel(level, Level.INFO))
          case _ ⇒ // ignore other loggers
        }
      }
    }

    conf
  }

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
