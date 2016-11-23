package eu.inn.facade

import java.io.{File, FileNotFoundException}

import com.typesafe.config.Config
import eu.inn.facade.raml.{RamlConfiguration, RamlConfigurationBuilder}
import org.raml.v2.api.RamlModelBuilder
import scaldi.Injector

object ConfigsFactory {

  def ramlConfig(appConfig: Config)(implicit inj: Injector): RamlConfiguration = {
    val ramlConfigPath = ramlFilePath(appConfig)
    val apiFile = new File(ramlConfigPath)
    if (!apiFile.exists()) {
      throw new FileNotFoundException(s"File ${apiFile.getAbsolutePath} doesn't exists")
    }
    val api = new RamlModelBuilder().buildApi(ramlConfigPath).getApiV10
    RamlConfigurationBuilder(api).build
  }

  private def ramlFilePath(config: Config): String = {
    val filePath = System.getProperty(FacadeConfigPaths.RAML_FILE, config.getString(FacadeConfigPaths.RAML_FILE))

    // it means that config contains absolute file path
    if (filePath.startsWith("/"))
      filePath
    // otherwise treat it as relative file path
    else {
      val r = Thread.currentThread().getContextClassLoader.getResource(filePath)
      if (r != null)
        r.getFile
      else
        filePath
    }
  }
}

object FacadeConfigPaths {
  val ROOT = "hyper-facade."
  val LOGGERS = ROOT + "loggers"
  val RAML_FILE = ROOT + "raml.file"
  val RAML_ROOT_PATH_PREFIX = ROOT + "raml.root-path"
  val RAML_STRICT_CONFIG = ROOT + "raml.strict-config"
  val HYPERBUS_GROUP = ROOT + "hyperbus.group-name"
  val INJECT_MODULES = ROOT + "inject-modules"
  val HTTP = ROOT + "http-transport"
  val SHUTDOWN_TIMEOUT = ROOT + "shutdown-timeout"
  val REQUEST_TIMEOUT = ROOT + "request-timeout"
  val MAX_SUBSCRIPTION_TRIES = ROOT + "max-subscription-tries"
  val REWRITE_COUNT_LIMIT = ROOT + "rewrite-count-limit"
  val FEED_MAX_STASHED_EVENTS_COUNT = ROOT + "feed-max-stashed-events-count"
}
