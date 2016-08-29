package eu.inn.facade.raml

import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.model.{RamlFilterFactory, TargetField}
import eu.inn.facade.model._
import org.raml.v2.api.model.v10.api.Api
import org.raml.v2.api.model.v10.bodies.Response
import org.raml.v2.api.model.v10.common.Annotable
import org.raml.v2.api.model.v10.datamodel.{ObjectTypeDeclaration, TypeDeclaration}
import org.raml.v2.api.model.v10.methods
import org.raml.v2.api.model.v10.methods.TraitRef
import org.raml.v2.api.model.v10.resources.Resource
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.control.NonFatal

class RamlConfigurationBuilder(val api: Api)(implicit inj: Injector) extends Injectable {
  val log = LoggerFactory.getLogger(getClass)

  var dataTypes: Map[String, TypeDefinition] = parseTypeDefinitions

  def build: RamlConfiguration = {
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
    RamlConfiguration(
      resourceMapWithFilters,
      urisAcc.result())
  }

  private def parseTypeDefinitions: Map[String, TypeDefinition] = {
    val typeDefinitions = api.types().foldLeft(Map.newBuilder[String, TypeDefinition]) { (typesMap, ramlTypeRaw) ⇒
      val ramlType = ramlTypeRaw.asInstanceOf[ObjectTypeDeclaration]
      val fields = ramlType.properties().foldLeft(Seq.newBuilder[Field]) { (parsedFields, ramlField) ⇒
        parsedFields += parseField(ramlField)
      }.result()

      val typeName = ramlType.name
      val parentTypeName = ramlType.`type`.isEmpty match {
        case true ⇒ None
        case false ⇒ Some(ramlType.`type`)
      }
      val annotations = extractAnnotations(ramlType)
      typesMap += typeName → TypeDefinition(typeName, parentTypeName, annotations, fields)
    }.result()

    withFlattenedFields(typeDefinitions)
  }

  private def withFlattenedFields(typeDefinitions: Map[String, TypeDefinition]): Map[String, TypeDefinition] = {
    typeDefinitions.foldLeft(Map.newBuilder[String, TypeDefinition]) { (tree, typeDefTuple) ⇒
      val (typeName, typeDef) = typeDefTuple
      val completedTypeDef = typeDef.copy(fields = withFlattenedSubFields(typeDef.fields, None, typeDefinitions))
      tree += typeName → completedTypeDef
    }.result()
  }

  private def withFlattenedSubFields(fields: Seq[Field], fieldNamePrefix: Option[String], typeDefinitions: Map[String, TypeDefinition]): Seq[Field] = {
    val initialFields = Seq.newBuilder[Field] ++= fields
    fields.foldLeft(initialFields) { (updatedFields, field) ⇒
      val typeDefOpt = typeDefinitions.get(field.typeName)
      val subFields = typeDefOpt match {
        case Some(typeDef) ⇒
          val subFieldNamePrefix = updatePrefix(field.name, fieldNamePrefix)
          val rawSubFields = withFlattenedSubFields(typeDef.fields, Some(subFieldNamePrefix), typeDefinitions)
          withNamePrefix(subFieldNamePrefix, rawSubFields)

        case None ⇒
          Seq.empty
      }
      updatedFields ++= subFields
    }.result()
  }

  private def updatePrefix(fieldName: String, previousPrefix: Option[String]): String = {
    previousPrefix match {
      case Some(prefix) ⇒
        s"$prefix.$fieldName"
      case None ⇒
        fieldName
    }
  }

  private def withNamePrefix(fieldNamePrefix: String, rawSubFields: Seq[Field]): Seq[Field] = {
    rawSubFields.foldLeft(Seq.newBuilder[Field]) { (renamedSubFields, subField) ⇒
      val newName = s"$fieldNamePrefix.${subField.name}"
      renamedSubFields += subField.copy(name = newName)
    }.result()
  }

  private def parseField(ramlField: TypeDeclaration): Field = {
    val name = ramlField.name
    val typeName = ramlField.`type`
    val annotations = extractAnnotations(ramlField)
    val field = Field(name, typeName, annotations)
    field
  }

  private def parseResource(currentUri: String, resource: Resource, parentAnnotations: Seq[RamlAnnotation]): (Map[String, ResourceConfig]) = {
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

  private def extractResourceMethod(currentUri: String, ramlMethod: methods.Method, resource: Resource): RamlResourceMethodConfig = {
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

  private def adjustParentAnnotations(childResourceRelativeUri: String, parentAnnotations: Seq[RamlAnnotation]): Seq[RamlAnnotation] = {
    val adjustedAnnotations = Seq.newBuilder[RamlAnnotation]
    parentAnnotations.foreach {
      case RewriteAnnotation(name, predicate, uri) ⇒
        val adjustedRewrittenUri = uri + childResourceRelativeUri
        adjustedAnnotations += RewriteAnnotation(name, predicate, adjustedRewrittenUri)
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
              typeDef.fields.foldLeft(SimpleFilterChain()) { (chain, field) ⇒
                val target = TargetField(typeDef.typeName, field) // we should pass all fields to support nested fields filtering
                chain ++ factory.createFilterChain(target)
              }
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
        val typeName = body.`type`
        typeNames += (contentType → Option(typeName))
      }
    }.result()
  }

  private def extractAnnotations(ramlField: Annotable): Seq[RamlAnnotation] = {
    val builder = Seq.newBuilder[RamlAnnotation]
    ramlField.annotations.foreach { annotation ⇒
      builder += RamlAnnotation(annotation.annotation.name, annotation.structuredValue.properties)
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
        val traitName = traitRef.`trait`().name()
        accumulator += Trait(traitName)
    }.result()
  }
}

object RamlConfigurationBuilder {
  def apply(api: Api)(implicit inj: Injector) = {
    new RamlConfigurationBuilder(api)
  }
}

private[raml] class RamlRequestResponseWrapper(val method: Option[methods.Method], val response: Option[Response]) {

  def body: java.util.List[TypeDeclaration] = {
    var bodyList = Seq[TypeDeclaration]()
    if (method.isDefined) bodyList = bodyList ++ method.get.body
    if (response.isDefined) bodyList = bodyList ++ response.get.body
    bodyList
  }

  def headers: java.util.List[TypeDeclaration] = {
    var bodyList = Seq[TypeDeclaration]()
    if (method.isDefined) bodyList = bodyList ++ method.get.headers
    if (response.isDefined) bodyList = bodyList ++ response.get.headers
    bodyList
  }
}

private[raml] object RamlRequestResponseWrapper {
  def apply(response: Response): RamlRequestResponseWrapper = {
    new RamlRequestResponseWrapper(None, Some(response))
  }

  def apply(method: methods.Method): RamlRequestResponseWrapper = {
    new RamlRequestResponseWrapper(Some(method), None)
  }
}
