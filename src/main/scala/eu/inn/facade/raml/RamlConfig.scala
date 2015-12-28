package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.mulesoft.raml1.java.parser.model.api.Api
import com.mulesoft.raml1.java.parser.model.methodsAndResources.{Resource, TraitRef}
import com.typesafe.config.Config

import scala.collection.JavaConversions._

class RamlConfig(val api: Api) {

  /**
   * Contains mapping from uri to resource configuration
   */
  val resourcesConfig = parseConfig()

  def traits(url: String, method: String): Set[String] = {
    val traits = resourcesConfig(url).traits
    traits.methodSpecificTraits
      .getOrElse(Method(method), traits.commonTraits)
      .map(foundTrait ⇒ foundTrait.name)
  }

  private def parseConfig(): Map[String, ResourceConfig] = {
    api.resources()
      .foldLeft(Map[String, ResourceConfig]()) { (accumulator, resource) ⇒
        val currentRelativeUri = resource.relativeUri().value()
        accumulator ++ parseResource(currentRelativeUri, resource)
      }
  }

  private def parseResource(currentUri: String, resource: Resource): Map[String, ResourceConfig] = {
    var methodSpecificTraits = Map[Method, Set[Trait]]()
    val commonResourceTraits = extractTraits(resource.is())
    resource.methods().foreach { ramlMethod ⇒
      val method = Method(ramlMethod.method())
      val methodTraits = extractTraits(ramlMethod.is())
      methodSpecificTraits += (method → (methodTraits ++ commonResourceTraits))
    }
    val resourceConfig = ResourceConfig(Traits(commonResourceTraits, methodSpecificTraits))

    resource.resources().foldLeft(Map(currentUri → resourceConfig)) { (configuration, resource) ⇒
      val childResourceRelativeUri = resource.relativeUri().value()
      configuration ++ parseResource(currentUri + childResourceRelativeUri, resource)
    }
  }

  private def extractTraits(traits: java.util.List[TraitRef]): Set[Trait] = {
    traits.foldLeft(Set[Trait]()) {
      (accumulator, traitRef) ⇒
        accumulator + Trait(traitRef.value.getRAMLValueName)
    }
  }
}

object RamlConfig {
  def apply(api: Api) = {
    new RamlConfig(api)
  }

  def apply(config: Config) = {
    val factory = new JavaNodeFactory
    val ramlConfigPath = ramlFilePath(config)
    new RamlConfig(factory.createApi(ramlConfigPath))
  }

  private def ramlFilePath(config: Config): String = {
    val fileRelativePath = config.getString("inn.facade.raml.file")
    val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
    val file = Paths.get(fileUri).toFile
    file.getCanonicalPath
  }
}