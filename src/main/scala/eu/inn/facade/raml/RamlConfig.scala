package eu.inn.facade.raml

import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.hyperbus.transport.api.uri._

class RamlConfig(val resourcesByUri: Map[String, ResourceConfig], uris: Seq[String]) {

  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method).map(foundTrait ⇒ foundTrait.name).distinct
  }

  def resourceUri(requestUriString: String): Uri = {
    val requestUri = Uri(requestUriString)
    matchUri(requestUri) match {
      case Some(uri) ⇒ uri
      case None ⇒ requestUri
    }
  }

  def matchUri(requestUri: Uri): Option[Uri] = {
    var foundUri: Option[Uri] = None
    for (uri ← uris if foundUri.isEmpty) {
      UriMatcher.matchUri(uri, requestUri) match {
        case uri @ Some(_) ⇒ foundUri = uri
        case None ⇒
      }
    }
    foundUri
  }

  private def traits(uriPattern: String, method: String): Seq[Trait] = {
    resourcesByUri.get(uriPattern) match {
      case Some(config) ⇒
        val traits = config.traits
        traits.methodSpecificTraits.getOrElse(Method(method), Seq.empty) ++ traits.commonTraits
      case None ⇒ Seq()
    }
  }
}

case class ResourceConfig(
                           traits: Traits,
                           annotations: Seq[Annotation],
                           methods: Map[Method, RamlResourceMethod],
                           filters: SimpleFilterChain
                         )

case class RamlResourceMethod(method: Method,
                              requests: RamlRequests,
                              responses: Map[Int, RamlResponses],
                              methodFilters: SimpleFilterChain)

case class RamlRequests(ramlContentTypes: Map[Option[ContentType], RamlContentType])
case class RamlResponses(ramlContentTypes: Map[Option[ContentType], RamlContentType])
case class RamlContentType(headers: Seq[Header], typeDefinition: TypeDefinition, filters: SimpleFilterChain)

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

case class TypeDefinition(typeName: String, annotations: Seq[Annotation], fields: Seq[Field])
object TypeDefinition {
  def apply(): TypeDefinition = {
    TypeDefinition(DataType.DEFAULT_TYPE_NAME, Seq.empty, Seq.empty)
  }
}

case class Field(name: String, typeName: String, annotations: Seq[Annotation], fields: Seq[Field])

case class Annotation(name: String, value: Option[RamlAnnotation])

object Annotation {
  val PRIVATE = "private"
  val CLIENT_LANGUAGE = "x-client-language"
  val CLIENT_IP = "x-client-ip"

  def apply(name: String): Annotation = Annotation(name, None)
}
