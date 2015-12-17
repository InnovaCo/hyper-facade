package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import eu.inn.util.ConfigComponent

trait RamlConfigComponent extends ConfigComponent {

  lazy val ramlConfig: RamlConfig = {
    val factory = new JavaNodeFactory
    RamlConfig(factory.createApi(ramlFilePath))
  }

  private def ramlFilePath: String = {
    val fileRelativePath = config.getString("inn.facade.raml.file")
    val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
    val file = Paths.get(fileUri).toFile
    file.getCanonicalPath
  }
}
