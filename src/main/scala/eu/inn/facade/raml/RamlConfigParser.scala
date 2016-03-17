package eu.inn.facade.raml

import com.mulesoft.raml1.java.parser.impl.datamodel.ObjectFieldImpl
import com.mulesoft.raml1.java.parser.model.api.Api
import com.mulesoft.raml1.java.parser.model.bodies.Response
import com.mulesoft.raml1.java.parser.model.datamodel.DataElement
import com.mulesoft.raml1.java.parser.model.methodsAndResources
import com.mulesoft.raml1.java.parser.model.methodsAndResources.{Resource, TraitRef}
import eu.inn.facade.filter.chain.Filters
import eu.inn.facade.model._
import eu.inn.facade.raml.annotations.RamlAnnotation
import scaldi.{Injectable, Injector}

import scala.collection.JavaConversions._

class RamlConfigParser(val api: Api)(implicit inj: Injector) extends Injectable {

  def parseRaml: RamlConfig = {
    val (resourcesByUri, uris) = api.resources()
      .foldLeft((Map.newBuilder[String, ResourceConfig], Seq.newBuilder[String])) { (accumulator, resource) ⇒
        val (resourceMap, uris) = accumulator
        val currentRelativeUri = resource.relativeUri().value()
        (resourceMap ++= parseResource(currentRelativeUri, resource), uris += currentRelativeUri)
      }
    new RamlConfig(resourcesByUri.result(), uris.result())
  }

  private def parseResource(currentUri: String, resource: Resource): Map[String, ResourceConfig] = {
    val traits = extractResourceTraits(resource) // todo: use annotations instead
    val requests = extractResourceRequests(currentUri, traits, resource)
    val responses = extractResourceResponses(currentUri, traits, resource)

    val filterChain = createFilterChain(TargetResource(currentUri), traits.commonTraits)

    val resourceConfig = ResourceConfig(traits, requests, responses, filterChain)

    val configuration = Map.newBuilder[String, ResourceConfig]
    configuration += (currentUri → resourceConfig)
    resource.resources().foldLeft(configuration) { (configuration, resource) ⇒
      val childResourceRelativeUri = resource.relativeUri().value()
      configuration ++= parseResource(currentUri + childResourceRelativeUri, resource)
    }.result()
  }

  private def createFilterChain(target: RamlTarget, traits: Seq[Trait]) = {
    traits.foldLeft(Filters.empty) { (filterChain, tr) ⇒
      val filterFactories = inject[Seq[RamlFilterFactory]](tr.name)
      filterFactories.foldLeft(filterChain) { (filterChainInner, factory) ⇒
        filterChainInner ++ factory.createFilters(target)
      }
    }
  }

  private def extractResourceRequests(currentUri: String, traits: Traits, resource: Resource): Requests = {
    val acc = Map.newBuilder[(Method, Option[ContentType]), DataStructure]
    val requestsByMethodAndType = resource.methods.foldLeft(acc) { (requestMap, ramlMethod) ⇒
      val method = Method(ramlMethod.method())
      val methodFilterChain = createFilterChain(TargetMethod(currentUri, method.name),
        traits.methodSpecificTraits.getOrElse(method, Seq.empty)
      )

      val dataTypes: Map[Option[ContentType], DataStructure] =
        extractTypeDefinitions(RamlRequestResponseWrapper(ramlMethod), methodFilterChain)
      dataTypes.foldLeft(requestMap) { (requestMap, mapEntry) ⇒
        val (contentType, dataStructure) = mapEntry
        requestMap += ((method, contentType) → dataStructure)
      }
    }.result()
    Requests(requestsByMethodAndType)
  }

  private def extractResourceResponses(currentUri: String, traits: Traits, resource: Resource): Responses = {
    val acc = Map.newBuilder[(Method, Int), DataStructure]
    val responsesByMethod = resource.methods.foldLeft(acc) { (responseMap, ramlMethod) ⇒
      val method = Method(ramlMethod.method())
      val methodFilterChain = createFilterChain(TargetMethod(currentUri, method.name),
        traits.methodSpecificTraits.getOrElse(method, Seq.empty)
      )

      ramlMethod.responses.foldLeft(responseMap) { (responseMap, ramlResponse) ⇒
        val statusCode: Int = Integer.parseInt(ramlResponse.code.value)
        val dataTypesMap: Map[Option[ContentType], DataStructure] =
          extractTypeDefinitions(RamlRequestResponseWrapper(ramlResponse), methodFilterChain)
        dataTypesMap.foldLeft(responseMap) { (responseMap, mapEntry) ⇒
          val dataStructure = mapEntry._2
          responseMap += ((method, statusCode) → dataStructure)
        }
      }
    }.result()
    Responses(responsesByMethod)
  }

  private def extractTypeDefinitions(ramlReqRspWrapper: RamlRequestResponseWrapper,
                                     methodFilters: Filters): Map[Option[ContentType], DataStructure] = {
    val headers = ramlReqRspWrapper.headers.foldLeft(Seq.newBuilder[Header]) { (headerList, ramlHeader) ⇒
      headerList += Header(ramlHeader.name())
    }.result()
    val typeNames: Map[Option[String], Option[String]] = getTypeNamesByContentType(ramlReqRspWrapper)
    typeNames.foldLeft(Map.newBuilder[Option[ContentType], DataStructure]) { (typeDefinitions, typeDefinition) ⇒
      val (contentTypeName, typeName) = typeDefinition
      val contentType: Option[ContentType] = contentTypeName match {
        case Some(name) ⇒ Some(ContentType(name))
        case None ⇒ None
      }
      val dataStructure = typeName match {
        case Some(name) ⇒ getTypeDefinition(name) match {
          case Some(td) ⇒
            val fields = td.asInstanceOf[ObjectFieldImpl].properties().foldLeft(Seq.newBuilder[Field]) {
              (fieldList, ramlField) ⇒
                val fieldName = ramlField.name
                val fieldType = ramlField.`type`.get(0)
                val field = Field(fieldName, DataType(fieldType, Seq.empty, extractAnnotations(ramlField)))
                fieldList += field
            }.result()

            val filterMap = fields.foldLeft(Seq.newBuilder[(RamlFilterFactory,Field)]) { (filterSeq, field) ⇒
              field.dataType.annotations.foreach { annotation ⇒
                val filterFactories = inject[Seq[RamlFilterFactory]](annotation.name)
                filterFactories.foreach { filterFactory : RamlFilterFactory ⇒
                  filterSeq += (filterFactory → field)
                }
              }
              filterSeq
            }.result().groupBy(_._1).map { case (k,v) ⇒
              k → v.map(_._2).distinct
            }

            val filterChain = filterMap.map { case (factory, filteredFields) ⇒
              val target = TargetFields(name, filteredFields)
              factory.createFilters(target)
            }.foldLeft (methodFilters) { (filterChain, next) ⇒
              filterChain ++ next
            }

            val body = Body(DataType(name, fields, Seq.empty))
            val dataType = DataStructure(headers, Some(body), filterChain)
            dataType

          case None ⇒ DataStructure(headers, Some(Body(DataType())), methodFilters)
        }

        case None ⇒ DataStructure(headers, Some(Body(DataType())), methodFilters)
      }
      typeDefinitions += (contentType → dataStructure)
    }.result()
  }

  private def getTypeNamesByContentType(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[String], Option[String]] = {
    if (ramlReqRspWrapper.body.isEmpty
      || ramlReqRspWrapper.body.get(0).`type`.isEmpty)
      Map(None → None)
    else {
      ramlReqRspWrapper.body.foldLeft(Map.newBuilder[Option[String], Option[String]]) { (typeNames, body) ⇒
        val contentType = if (body.name == null || body.name == "body" || body.name.equalsIgnoreCase("none")) None else Some(body.name)
        val typeName = body.`type`.get(0)
        typeNames += (contentType → Option(typeName))
      }
    }.result()
  }

  private def getTypeDefinition(typeName: String): Option[DataElement] = {
    api.types.foldLeft[Option[DataElement]](None) { (matchedType, typeDef) ⇒
      matchedType match {
        case Some(foundType) ⇒ Some(foundType)
        case None ⇒
          if (typeDef.name == typeName) Some(typeDef)
          else None
      }
    }
  }

  private def extractAnnotations(ramlField: DataElement): Seq[Annotation] = {
    ramlField.annotations.foldLeft(Seq.newBuilder[Annotation]) { (annotations, annotation) ⇒
      val value = annotation.value() match {
        case x: RamlAnnotation ⇒ Some(x)
        case _ ⇒ None
      }
      annotations += Annotation(annotation.value.getRAMLValueName, value)
    }.result()
  }

  private def extractResourceTraits(resource: Resource): Traits = {
    val commonResourceTraits = extractTraits(resource.is())
    val methodSpecificTraits = resource.methods().foldLeft(Map.newBuilder[Method, Seq[Trait]]) { (specificTraits, ramlMethod) ⇒
      val method = Method(ramlMethod.method)
      val methodTraits = extractTraits(ramlMethod.is())
      specificTraits += (method → (methodTraits ++ commonResourceTraits))
    }.result()
    Traits(commonResourceTraits, methodSpecificTraits)
  }

  private def extractTraits(traits: java.util.List[TraitRef]): Seq[Trait] = {
    traits.foldLeft(Seq.newBuilder[Trait]) {
      (accumulator, traitRef) ⇒
        val traitName = traitRef.value.getRAMLValueName
        accumulator += Trait(traitName)
    }.result()
  }
}

object RamlConfigParser {
  def apply(api: Api)(implicit inj: Injector) = {
    new RamlConfigParser(api)
  }
}

private[raml] class RamlRequestResponseWrapper(val method: Option[methodsAndResources.Method], val response: Option[Response]) {

  def body: java.util.List[DataElement] = {
    var bodyList = Seq[DataElement]()
    if (method.isDefined) bodyList = bodyList ++ method.get.body
    if (response.isDefined) bodyList = bodyList ++ response.get.body
    bodyList
  }

  def headers: java.util.List[DataElement] = {
    var bodyList = Seq[DataElement]()
    if (method.isDefined) bodyList = bodyList ++ method.get.headers
    if (response.isDefined) bodyList = bodyList ++ response.get.headers
    bodyList
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
