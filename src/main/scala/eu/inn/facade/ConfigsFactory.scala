package eu.inn.facade

import java.nio.file.Paths

import ch.qos.logback.classic.{Level, Logger}
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.facade.raml.{RamlConfig, RamlConfigParser, RamlConfigParser$}
import org.slf4j.LoggerFactory

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
    if (conf.hasPath("inn.util.loggers")) {
      conf.getObject("inn.util.loggers").toMap foreach { case (name, obj) ⇒
        val level = obj.atPath("/").getString("/")

        LoggerFactory.getLogger(name) match {
          case l: Logger ⇒ l.setLevel(Level.toLevel(level, Level.INFO))
          case _ ⇒ // ignore other loggers
        }
      }
    }

    conf
  }

  def ramlConfig(appConfig: Config): RamlConfig = {
    val factory = new JavaNodeFactory
    val ramlConfigPath = ramlFilePath(appConfig)
    RamlConfigParser(factory.createApi(ramlConfigPath)).parseRaml
  }

  private def ramlFilePath(config: Config): String = {
    val fileRelativePath = config.getString("inn.facade.raml.file")
    val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
    val file = Paths.get(fileUri).toFile
    file.getCanonicalPath
  }
}
