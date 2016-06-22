package eu.inn.facade.raml

import com.mulesoft.raml1.java.parser.impl.datamodel.ObjectFieldImpl
import com.mulesoft.raml1.java.parser.model.api.Api
import com.mulesoft.raml1.java.parser.model.bodies.Response
import com.mulesoft.raml1.java.parser.model.common.RAMLLanguageElement
import com.mulesoft.raml1.java.parser.model.datamodel.DataElement
import com.mulesoft.raml1.java.parser.model.methodsAndResources
import com.mulesoft.raml1.java.parser.model.methodsAndResources.{Resource, TraitRef}
import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.model.{RamlFilterFactory, TargetFields}
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.rewrite
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.control.NonFatal

class RamlConfigParser(val api: Api)(implicit inj: Injector) extends Injectable {
  val log = LoggerFactory.getLogger(getClass)

  var dataTypes: Map[String, TypeDefinition] = parseTypeDefinitions()

  def parseRaml: RamlConfig = {
    val resourcesByUriAcc = Map.newBuilder[String, ResourceConfig]
    val urisAcc = Seq.newBuilder[String]
    api.resources()
      .foldLeft((resourcesByUriAcc, urisAcc)) { (accumulator, resource) ⇒
        val (resourceMap, uris) = accumulator
        val currentRelativeUri = resource.relativeUri().value()
        val resourceData = parseResource(currentRelativeUri, resource, Seq.empty)
        (resourceMap ++= resourceData,
          uris += currentRelativeUri)
    }
    val resourceMapWithFilters = new RamlConfigFiltersInjector(resourcesByUriAcc.result()).withResourceFilters()
    new RamlConfig(
      resourceMapWithFilters,
      urisAcc.result())
  }

  private def parseTypeDefinitions(): Map[String, TypeDefinition] = {
    val typeDefinitions = api.types().foldLeft(Map.newBuilder[String, TypeDefinition]) { (typesMap, ramlTypeRaw) ⇒
      val ramlType = ramlTypeRaw.asInstanceOf[ObjectFieldImpl]
      val fields = ramlType.properties().foldLeft(Seq.newBuilder[Field]) { (parsedFields, ramlField) ⇒
        parsedFields += parseField(ramlField)
      }.result()

      val typeName = ramlType.name
      val annotations = extractAnnotations(ramlType)
      typesMap += typeName → TypeDefinition(typeName, annotations, fields)
    }.result()

    fillTypeTree(typeDefinitions)
  }

  private def fillTypeTree(typeDefinitions: Map[String, TypeDefinition]): Map[String, TypeDefinition] = {
    typeDefinitions.foldLeft(Map.newBuilder[String, TypeDefinition]) { (tree, typeDefTuple) ⇒
      val (typeName, typeDef) = typeDefTuple
      val completedTypeDef = typeDef.copy(fields = fillFieldsTypes(typeDef.fields, typeDefinitions))
      tree += typeName → completedTypeDef
    }.result()
  }

  private def fillFieldsTypes(fields: Seq[Field], typeDefinitions: Map[String, TypeDefinition]): Seq[Field] = {
    fields.foldLeft(Seq.newBuilder[Field]) { (updatedFields, field) ⇒
      val typeDefOpt = typeDefinitions.get(field.typeName)
      val subFields = typeDefOpt match {
        case Some(typeDef) ⇒ fillFieldsTypes(typeDef.fields, typeDefinitions)
        case None ⇒ Seq.empty
      }
      updatedFields += field.copy(fields = subFields)
    }.result()
  }

  private def parseField(ramlField: DataElement): Field = {
    val name = ramlField.name
    val typeName = ramlField.`type`.headOption.getOrElse(DataType.DEFAULT_TYPE_NAME)
    val annotations = extractAnnotations(ramlField)
    val field = Field(name, typeName, annotations, Seq.empty)
    field
  }

  private def parseResource(currentUri: String, resource: Resource, parentAnnotations: Seq[Annotation]): (Map[String, ResourceConfig]) = {
    val traits = extractResourceTraits(resource) // todo: eliminate?

    val adjustedParentAnnotations = adjustParentAnnotations(resource.relativeUri.value(), parentAnnotations)
    val resourceAnnotations = adjustedParentAnnotations ++ extractAnnotations(resource)
    val resourceMethods = extractResourceMethods(currentUri, resource)

    val resourceConfig = ResourceConfig(traits, resourceAnnotations, resourceMethods, SimpleFilterChain())

    val configuration = Map.newBuilder[String, ResourceConfig]
    configuration += (currentUri → resourceConfig)
    resource.resources().foldLeft(configuration) { (configuration, childResource) ⇒
      val childResourceRelativeUri = childResource.relativeUri().value()
      val resourceData = parseResource(currentUri + childResourceRelativeUri, childResource, resourceAnnotations)
      configuration ++= resourceData
    }
    configuration.result()
  }

  private def extractResourceMethods(currentUri: String, resource: Resource): Map[Method, RamlResourceMethodConfig] = {
    val builder = Map.newBuilder[Method, RamlResourceMethodConfig]
    resource.methods.foreach { ramlMethod ⇒
      builder += Method(ramlMethod.method) → extractResourceMethod(currentUri, ramlMethod, resource)
    }
    builder.result()
  }

  private def extractResourceMethod(currentUri: String, ramlMethod: methodsAndResources.Method, resource: Resource): RamlResourceMethodConfig = {
    val methodAnnotations = extractAnnotations(ramlMethod)
    val method = Method(ramlMethod.method())

    val ramlRequests = RamlRequests(extractRamlContentTypes(RamlRequestResponseWrapper(ramlMethod)))

    val ramlResponses = Map.newBuilder[Int, RamlResponses]
    ramlMethod.responses.foreach { ramlResponse ⇒
      val statusCode = ramlResponse.code.value.toInt
      val responseRamlContentTypes = extractRamlContentTypes(RamlRequestResponseWrapper(ramlResponse))
      ramlResponses += statusCode → RamlResponses(responseRamlContentTypes)
    }

    RamlResourceMethodConfig(method, methodAnnotations, ramlRequests, ramlResponses.result(), SimpleFilterChain())
  }

  private def adjustParentAnnotations(childResourceRelativeUri: String, parentAnnotations: Seq[Annotation]): Seq[Annotation] = {
    val adjustedAnnotations = Seq.newBuilder[Annotation]
    parentAnnotations.foreach {
      case Annotation(Annotation.REWRITE, Some(ann)) ⇒
        val rewriteAnnotation = ann.asInstanceOf[rewrite]
        val adjustedRewrittenUri = rewriteAnnotation.getUri + childResourceRelativeUri
        val adjustedRewriteAnn = new rewrite()
        adjustedRewriteAnn.setUri(adjustedRewrittenUri)
        adjustedAnnotations += Annotation(Annotation.REWRITE, Some(adjustedRewriteAnn))
      case otherAnn ⇒ adjustedAnnotations += otherAnn
    }
    adjustedAnnotations.result()
  }

  private def extractRamlContentTypes(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[ContentType], RamlContentTypeConfig] = {
    val headers = ramlReqRspWrapper.headers.foldLeft(Seq.newBuilder[Header]) { (headerList, ramlHeader) ⇒
      headerList += Header(ramlHeader.name())
    }.result()
    val typeNames: Map[Option[String], Option[String]] = getTypeNamesByContentType(ramlReqRspWrapper)

    typeNames.foldLeft(Map.newBuilder[Option[ContentType], RamlContentTypeConfig]) { (ramlContentTypes, typeDefinition) ⇒
      val (contentTypeName, typeName) = typeDefinition
      val contentType: Option[ContentType] = contentTypeName match {
        case Some(name) ⇒ Some(ContentType(name))
        case None ⇒ None
      }
      val ramlContentType = typeName match {
        case Some(name) ⇒ dataTypes.get(name) match {
          case Some(typeDef) ⇒
            val filterFactories = typeDef.fields.foldLeft(Seq.newBuilder[RamlFilterFactory]) { (filterFactories, field) ⇒
              fieldFilters(filterFactories, field)
            }.result().distinct

            val filterChain = filterFactories.map { factory ⇒
              val target = TargetFields(typeDef.typeName, typeDef.fields) // we should pass all fields to support nested fields filtering
              factory.createFilterChain(target)
            }.foldLeft (SimpleFilterChain()) { (filterChain, next) ⇒
              filterChain ++ next
            }
            RamlContentTypeConfig(headers, typeDef, filterChain)

          case None ⇒ RamlContentTypeConfig(headers, TypeDefinition(), SimpleFilterChain())
        }

        case None ⇒ RamlContentTypeConfig(headers, TypeDefinition(), SimpleFilterChain())
      }
      ramlContentTypes += (contentType → ramlContentType)
    }.result()
  }

  def fieldFilters(filterFactories: mutable.Builder[RamlFilterFactory, Seq[RamlFilterFactory]], field: Field): mutable.Builder[RamlFilterFactory, Seq[RamlFilterFactory]] = {
    field.annotations.foreach { annotation ⇒
      try {
        filterFactories += inject[RamlFilterFactory](annotation.name)
      }
      catch {
        case NonFatal(e) ⇒
          log.error(s"Can't inject filter for annotation ${annotation.name}", e)
      }
    }
    field.fields.foreach(subField ⇒ fieldFilters(filterFactories, subField))
    filterFactories
  }

  private def getTypeNamesByContentType(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[String], Option[String]] = {
    if (ramlReqRspWrapper.body.isEmpty
      || ramlReqRspWrapper.body.get(0).`type`.isEmpty)
      Map(None → None)
    else {
      ramlReqRspWrapper.body.foldLeft(Map.newBuilder[Option[String], Option[String]]) { (typeNames, body) ⇒
        val contentType = Option(body.name).map(_.toLowerCase) match {
          case None | Some("body") | Some("none") ⇒ None
          case other ⇒ FacadeHeaders.httpContentTypeToGeneric(other)
        }
        val typeName = body.`type`.get(0)
        typeNames += (contentType → Option(typeName))
      }
    }.result()
  }

  private def extractAnnotations(ramlField: RAMLLanguageElement): Seq[Annotation] = {
    val builder = Seq.newBuilder[Annotation]
    ramlField.annotations.foreach { annotation ⇒
      val value = annotation.value() match {
        case x: RamlAnnotation ⇒ Some(x)
        case _ ⇒ None
      }
      builder += Annotation(annotation.value.getRAMLValueName, value)
    }
    builder.result()
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
