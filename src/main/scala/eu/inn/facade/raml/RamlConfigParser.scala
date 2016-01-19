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

import eu.inn.facade.raml.DataType._

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
    val acc = Map[(Method, Option[ContentType]), DataStructure]()
    val requestsByMethodAndType = resource.methods.foldLeft(acc) { (requestMap, ramlMethod) ⇒
      val method = Method(ramlMethod.method())
      val dataTypes: Map[Option[ContentType], DataStructure] = extractTypeDefinitions(RamlRequestResponseWrapper(ramlMethod))
      dataTypes.foldLeft(requestMap) { (requestMap, mapEntry) ⇒
        val (contentType, dataStructure) = mapEntry
        requestMap + (((method, contentType), dataStructure))
      }
    }
    Requests(requestsByMethodAndType)
  }

  private def extractResourceResponses(resource: Resource): Responses = {
    val acc = Map[(Method, Int), DataStructure]()
    val responsesByMethod = resource.methods.foldLeft(acc) { (responseMap, ramlMethod) ⇒
      val method = Method(ramlMethod.method())
      ramlMethod.responses.foldLeft(responseMap) { (responseMap, ramlResponse) ⇒
        val statusCode: Int = Integer.parseInt(ramlResponse.code.value)
        val dataTypesMap: Map[Option[ContentType], DataStructure] = extractTypeDefinitions(RamlRequestResponseWrapper(ramlResponse))
        dataTypesMap.foldLeft(responseMap) { (responseMap, mapEntry) ⇒
          val dataStructure = mapEntry._2
          responseMap + (((method, statusCode), dataStructure))
        }
      }
    }
    Responses(responsesByMethod)
  }

  private def extractTypeDefinitions(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[ContentType], DataStructure] = {
    val headers = ramlReqRspWrapper.headers.foldLeft(Seq[Header]()) { (headerList, ramlHeader) ⇒
      headerList :+ Header(ramlHeader.name())
    }
    val typeNames: Map[Option[String], Option[String]] = getTypeNamesByContentType(ramlReqRspWrapper)
    typeNames.foldLeft(Map[Option[ContentType], DataStructure]()) { (typeDefinitions, typeDefinition) ⇒
      val (contentTypeName, typeName) = typeDefinition
      val contentType: Option[ContentType] = contentTypeName match {
        case Some(name) ⇒ Some(ContentType(name))
        case None ⇒ None
      }
      val dataStructure = typeName match {
        case Some(name) ⇒ getTypeDefinition(name) match {
          case Some(typeDefinition) ⇒
            val fields = typeDefinition.asInstanceOf[ObjectFieldImpl].properties().foldLeft(Seq[Field] ()) {
              (fieldList, ramlField) ⇒
                val fieldName = ramlField.name
                val fieldType = ramlField.`type`.get(0)
                val field = Field(fieldName, DataType(fieldType, Seq(), extractAnnotations(ramlField)))
                fieldList :+ field
            }
            val body = Body(DataType(name, fields, Seq()))
            val dataType = DataStructure(headers, Some(body))
            dataType
          case None ⇒ DataStructure(headers, Some(Body(DataType())))
        }

        case None ⇒ DataStructure(headers, Some(Body(DataType())))
      }
      typeDefinitions + ((contentType, dataStructure))
    }
  }

  private def getTypeNamesByContentType(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[String], Option[String]] = {
    if (ramlReqRspWrapper.body.isEmpty
      || ramlReqRspWrapper.body.get(0).`type`.isEmpty)
      Map(None → None)
    else {
      ramlReqRspWrapper.body.foldLeft(Map[Option[String], Option[String]]()) { (typeNames, body) ⇒
        val contentType = if (body.name == null || body.name == "body") None else Some(body.name)
        val typeName = body.`type`.get(0)
        val typeNameOption = if ( typeName == null) None else Some(typeName)
        typeNames + ((contentType, typeNameOption))
      }
    }
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
      val method = Method(ramlMethod.method)
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
