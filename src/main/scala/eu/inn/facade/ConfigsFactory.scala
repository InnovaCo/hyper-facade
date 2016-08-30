package eu.inn.facade

import java.io.{File, FileNotFoundException}

import com.mulesoft.raml.webpack.holders.JSConsole
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.Config
import eu.inn.facade.raml.{RamlConfiguration, RamlConfigurationBuilder}
import eu.inn.facade.utils.raml.JsToLogConsole
import scaldi.Injector

object ConfigsFactory {

  def ramlConfig(appConfig: Config)(implicit inj: Injector): RamlConfiguration = {
    val ramlFactory = new JavaNodeFactory()
    val existingConsole = ramlFactory.getBindings.get("console").asInstanceOf[JSConsole]
    ramlFactory.getBindings.put("console", new JsToLogConsole(existingConsole.engine))

    val ramlConfigPath = ramlFilePath(appConfig)
    val apiFile = new File(ramlConfigPath)
    if (!apiFile.exists()) {
      throw new FileNotFoundException(s"File ${apiFile.getAbsolutePath} doesn't exists")
    }
    val api = ramlFactory.createApi(apiFile.getAbsolutePath)
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
  val MAX_SUBSCRIPTION_TRIES = ROOT + "max-subscription-tries"
  val REWRITE_COUNT_LIMIT = ROOT + "rewrite-count-limit"
}
