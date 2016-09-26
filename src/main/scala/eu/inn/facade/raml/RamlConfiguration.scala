package eu.inn.facade.raml

import eu.inn.facade.filter.chain.SimpleFilterChain

case class RamlConfiguration(resourcesByUri: Map[String, ResourceConfig], uris: Seq[String])

case class ResourceConfig(
                           traits: Traits,
                           annotations: Seq[RamlAnnotation],
                           methods: Map[Method, RamlResourceMethodConfig],
                           filters: SimpleFilterChain
                         )

case class RamlResourceMethodConfig(method: Method,
                                    annotations: Seq[RamlAnnotation],
                                    requests: RamlRequests,
                                    responses: Map[Int, RamlResponses],
                                    methodFilters: SimpleFilterChain)

case class RamlRequests(ramlContentTypes: Map[Option[ContentType], RamlContentTypeConfig])
case class RamlResponses(ramlContentTypes: Map[Option[ContentType], RamlContentTypeConfig])
case class RamlContentTypeConfig(headers: Seq[Header], typeDefinition: TypeDefinition, filters: SimpleFilterChain)

case class Traits(commonTraits: Seq[Trait], methodSpecificTraits: Map[Method, Seq[Trait]])
case class Trait(name: String, parameters: Map[String, String])
object Trait {
  def apply(name: String): Trait = {
    Trait(name, Map())
  }
}

case class Method(name: String)
object Method {
  val POST = "post"
  val GET = "get"
  val PUT = "put"
  val DELETE = "delete"
  val PATCH = "patch"
}

case class ContentType(mediaType: String)

case class Header(name: String)

object DataType {
  val DEFAULT_TYPE_NAME = "string"
}

case class TypeDefinition(typeName: String, parentTypeName: Option[String], annotations: Seq[RamlAnnotation], fields: Seq[Field])
object TypeDefinition {
  def apply(): TypeDefinition = {
    TypeDefinition(DataType.DEFAULT_TYPE_NAME, None, Seq.empty, Seq.empty)
  }
}

case class Field(name: String, typeName: String, annotations: Seq[RamlAnnotation])
