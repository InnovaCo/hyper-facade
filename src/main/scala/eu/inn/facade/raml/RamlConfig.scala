package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.mulesoft.raml1.java.parser.model.api.Api
import com.mulesoft.raml1.java.parser.model.methodsAndResources.{Resource, TraitRef}
import com.typesafe.config.Config

import scala.collection.JavaConversions._

// review: надо разделить на 2 стадии, подготовка (кеширование результатов RAML -> internal state)
// и вторая стадия - возврат результатов из internal state, чтобы возврат был с предсказуемым временем (алгоритмической сложностью)
class RamlConfig(val api: Api) {

  def isPingRequest(url: String): Boolean = {
    false
  }

  def traits(url: String, method: String): Seq[String] = {
    findResource(url) match {
      case Some(targetResource) ⇒
        targetResource.methods().find { ramlMethod ⇒
          ramlMethod.method() == method
        } match {
          case Some(ramlMethod) ⇒
            extractTraitNames(ramlMethod.is())

          case None ⇒ extractTraitNames(targetResource.is())
        }
      case None ⇒ Seq()
    }
  }

  private def extractTraitNames(traits: java.util.List[TraitRef]): Seq[String] = {
    traits.foldLeft(Seq[String]()) {
      (accumulator, traitRef) ⇒
        accumulator :+ traitRef.value.getRAMLValueName
    }
  }

  private def findResource(url: String): Option[Resource] = {
    val pathSegments = splitUrl(url)
    val resources = api.resources()
    findResource(pathSegments, resources)
  }

  private def splitUrl(url: String): Array[String] = {
    url.split('/').filter( node ⇒ node.nonEmpty)
  }

  private def findResource(pathSegments: Array[String], resources: java.util.List[Resource]): Option[Resource] = {
    var found: Option[Resource] = None
    for (resource ← resources if found.isEmpty) {
      found = findResource(pathSegments, resource)
    }
    found
  }

  private def findResource(pathSegments: Array[String], resource: Resource): Option[Resource] = {
    if (matched(pathSegments.head, normalizeResourceUri(resource))) {   // We matched one more node of full url
      if (pathSegments.tail.isEmpty) Some(resource)    // We matched all nodes of url
      else if (resource.resources.nonEmpty) findResource(pathSegments.tail, resource.resources())   // We should continue matching of url tail
      else None   // There are no more resources in resource tree of RAML configuration but full url is not matched yet
    }
    else None   // Node of url is not matched with current resource of resource tree of RAML configuration
  }

  private def normalizeResourceUri(resource: Resource): String = {
    resource.relativeUri().value().replace("/", "")
  }

  private def matched(requestUrlNode: String, ramlResourceNodeName: String): Boolean = {
    if (ramlResourceNodeName.startsWith("{") && ramlResourceNodeName.endsWith("}")) true
    else {
      val answer = requestUrlNode == ramlResourceNodeName
      answer
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
