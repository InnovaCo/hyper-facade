package eu.inn.facade

import java.io.{File, IOException}

import com.mulesoft.raml.webpack.holders.JSConsole
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.Config
import eu.inn.facade.raml.{RamlConfig, RamlConfigParser}
import eu.inn.facade.utils.raml.JsToLogConsole
import scaldi.Injector

class ConfigsFactory {

  def ramlConfig(appConfig: Config)(implicit inj: Injector): RamlConfig = {
    val ramlFactory = new JavaNodeFactory()
    val existingConsole = ramlFactory.getBindings.get("console").asInstanceOf[JSConsole]
    ramlFactory.getBindings.put("console", new JsToLogConsole(existingConsole.engine))

    val ramlConfigPath = ramlFilePath(appConfig)
    val apiFile = new File(ramlConfigPath)
    if (!apiFile.exists()) {
      throw new IOException(s"File ${apiFile.getAbsolutePath} doesn't exists")
    }
    val api = ramlFactory.createApi(apiFile.getAbsolutePath)
    RamlConfigParser(api).parseRaml
  }

  private def ramlFilePath(config: Config): String = {
    val filePath = config.getString(FacadeConfig.RAML_FILE)

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

object FacadeConfig {
  val ROOT = "hyperbus-facade."
  val PRIVATE_ADDRESSES = ROOT + "private.addresses"
  val PRIVATE_NETWORKS = ROOT + "private.networks"
  val LOGGERS = ROOT + "loggers"
  val RAML_FILE = ROOT + "raml.file"
  val RAML_ROOT_PATH = ROOT + "raml.root-path"
  val HYPERBUS_GROUP = ROOT + "hyperbus.group-name"
  val GRAPHITE = ROOT + "graphite"
  val INJECT_MODULES = ROOT + "inject-modules"
  val HTTP = ROOT + "http-transport"
  val SHUTDOWN_TIMEOUT = ROOT + "shutdown-timeout"
  val MAX_RESUBSCRIPTIONS = ROOT + "max-resubscriptions"
}
