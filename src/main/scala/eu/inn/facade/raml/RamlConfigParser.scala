package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.mulesoft.raml1.java.parser.impl.datamodel.ObjectFieldImpl
import com.mulesoft.raml1.java.parser.model.api.Api
import com.mulesoft.raml1.java.parser.model.bodies.Response
import com.mulesoft.raml1.java.parser.model.datamodel.DataElement
import com.mulesoft.raml1.java.parser.model.methodsAndResources
import com.mulesoft.raml1.java.parser.model.methodsAndResources.{Resource, TraitRef}
import com.typesafe.config.Config

import scala.collection.JavaConversions._

class RamlConfigParser(val api: Api) {

  def parseRaml: RamlConfig = {
    val resourcesByUrl = api.resources()
      .foldLeft(Map[String, ResourceConfig]()) { (accumulator, resource) ⇒
        val currentRelativeUri = resource.relativeUri().value()
        accumulator ++ parseResource(currentRelativeUri, resource)
      }
    new RamlConfig(resourcesByUrl)
  }

  private def parseResource(currentUri: String, resource: Resource): Map[String, ResourceConfig] = {
    val traits = extractResourceTraits(resource)
    val requests = extractResourceRequests(resource)
    val responses = extractResourceResponses(resource)
    val resourceConfig = ResourceConfig(traits, requests, responses)

    resource.resources().foldLeft(Map(currentUri → resourceConfig)) { (configuration, resource) ⇒
      val childResourceRelativeUri = resource.relativeUri().value()
      configuration ++ parseResource(currentUri + childResourceRelativeUri, resource)
    }
  }

  private def extractResourceRequests(resource: Resource): Requests = {
    val requestsByMethod = resource.methods.foldLeft(Map[Method, DataStructure]()) { (requestMap, ramlMethod) ⇒
      val dataType: DataStructure = extractTypeDefinition(RamlRequestResponseWrapper(ramlMethod))
      requestMap + ((Method(ramlMethod.method()), dataType))
    }
    Requests(requestsByMethod)
  }

  private def extractResourceResponses(resource: Resource): Responses = {
    var responses = Map[(Method, Int), DataStructure]()
    resource.methods.foreach { ramlMethod ⇒
      val method = Method(ramlMethod.method())
      val responsesByCodes = ramlMethod.responses.foreach { ramlResponse ⇒
        val statusCode: Int = Integer.parseInt(ramlResponse.code.value).toInt
        val dataType = extractTypeDefinition(RamlRequestResponseWrapper(ramlResponse))
        responses += (((method, statusCode), dataType))
      }
    }
    Responses(responses)
  }

  private def extractTypeDefinition(ramlReqRspWrapper: RamlRequestResponseWrapper): DataStructure = {
    val headers = ramlReqRspWrapper.headers.foldLeft(Seq[Header]()) { (headerList, ramlHeader) ⇒
      headerList :+ Header(ramlHeader.name())
    }
    val typeName = getTypeName(ramlReqRspWrapper)
    typeName match {
      case Some(name) ⇒ getTypeDefinition(name) match {
        case Some(typeDefinition) ⇒
          val fields = typeDefinition.asInstanceOf[ObjectFieldImpl].properties().foldLeft(Seq[Field] ()) {
            (fieldList, ramlField) ⇒
              val field = Field(ramlField.name(), extractAnnotations(ramlField))
              fieldList :+ field
          }
          val body = Body(fields)
          val dataType = DataStructure(headers, body)
          dataType
        case None ⇒ DataStructure(headers)
      }

      case None ⇒ DataStructure(headers)
    }
  }

  private def getTypeName(ramlReqRspWrapper: RamlRequestResponseWrapper): Option[String] = {
    if (ramlReqRspWrapper.body.isEmpty
      || ramlReqRspWrapper.body.get(0).`type`.isEmpty)
      None
    else Some(ramlReqRspWrapper.body.get(0).`type`.get(0))
  }

  private def getTypeDefinition(typeName: String): Option[DataElement] = {
    api.types.foldLeft[Option[DataElement]](None) { (matchedType, typeDef) ⇒
      matchedType match {
        case Some(foundType) ⇒ Some(foundType)
        case None ⇒ {
          if (typeDef.name == typeName) Some(typeDef)
          else None
        }
      }
    }
  }

  private def extractAnnotations(ramlField: DataElement): Seq[Annotation] = {
    ramlField.annotations.foldLeft(Seq[Annotation]()) { (annotations, annotation) ⇒
      annotations :+ Annotation(annotation.value.getRAMLValueName)
    }
  }

  private def extractResourceTraits(resource: Resource): Traits = {
    val commonResourceTraits = extractTraits(resource.is())
    val methodSpecificTraits = resource.methods().foldLeft(Map[Method, Seq[Trait]]()) { (specificTraits, ramlMethod) ⇒
      val method = Method(ramlMethod.method())
      val methodTraits = extractTraits(ramlMethod.is())
      specificTraits + ((method, (methodTraits ++ commonResourceTraits)))
    }
    Traits(commonResourceTraits, methodSpecificTraits)
  }

  private def extractTraits(traits: java.util.List[TraitRef]): Seq[Trait] = {
    traits.foldLeft(Seq[Trait]()) {
      (accumulator, traitRef) ⇒
        accumulator :+ Trait(traitRef.value.getRAMLValueName)
    }
  }
}

object RamlConfigParser {
  def apply(api: Api) = {
    new RamlConfigParser(api)
  }

  def apply(config: Config) = {
    val factory = new JavaNodeFactory
    val ramlConfigPath = ramlFilePath(config)
    new RamlConfigParser(factory.createApi(ramlConfigPath))
  }

  private def ramlFilePath(config: Config): String = {
    val fileRelativePath = config.getString("inn.facade.raml.file")
    val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
    val file = Paths.get(fileUri).toFile
    file.getCanonicalPath
  }
}

private[raml] class RamlRequestResponseWrapper(val method: Option[methodsAndResources.Method], val response: Option[Response]) {

  def body: java.util.List[DataElement] = {
    if (method == None) response.get.body
    else method.get.body
  }

  def headers: java.util.List[DataElement] = {
    if (method == None) response.get.headers
    else method.get.headers
  }
}

private[raml] object RamlRequestResponseWrapper {
  def apply(response: Response): RamlRequestResponseWrapper = {
    new RamlRequestResponseWrapper(None, Some(response))
  }

  def apply(method: methodsAndResources.Method): RamlRequestResponseWrapper = {
    new RamlRequestResponseWrapper(Some(method), None)
  }
}
