package eu.inn.facade.raml

import eu.inn.facade.raml.annotations.RamlAnnotation
import eu.inn.hyperbus.transport.api
import eu.inn.hyperbus.transport.api.uri._

class RamlConfig(val resourcesByUri: Map[String, ResourceConfig], uris: Seq[String]) {

  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method) map (foundTrait ⇒ foundTrait.name)
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

  def requestDataStructure(uriPattern: String, method: String, contentType: Option[String]): Option[DataStructure] = {
    resourcesByUri.get(uriPattern) match {
      case Some(resourceConfig) ⇒ resourceConfig.requests.dataStructures.get(Method(method), getContentType(contentType))
      case None ⇒ None
    }
  }

  def responseDataStructure(uriPattern: String, method: String, statusCode: Int): Option[DataStructure] = {
    resourcesByUri.get(uriPattern) match {
      case Some(resourceConfig) ⇒ resourceConfig.responses.dataStructures.get(Method(method), statusCode)
      case None ⇒ None
    }
  }

  def responseDataStructures(uri: api.uri.Uri, method: String): Seq[DataStructure] = {
    resourcesByUri.get(uri.pattern.specific) match {
      case Some(resourceConfig) ⇒
        resourceConfig.responses.dataStructures.foldLeft(Seq.newBuilder[DataStructure]) { (structuresByMethod, kv) ⇒
          val (httpMethod, _) = kv._1
          val structure = kv._2
          if (httpMethod == Method(method)) structuresByMethod += structure
          else structuresByMethod
        }.result()

      case None ⇒ Seq()
    }
  }

  private def traits(uriPattern: String, method: String): Seq[Trait] = {
    resourcesByUri.get(uriPattern) match {
      case Some(config) ⇒
        val traits = config.traits
        traits.methodSpecificTraits.getOrElse(Method(method), Seq.empty) ++ traits.commonTraits
      case None ⇒ Seq()
    }
  }

  private def getContentType(contentTypeName: Option[String]): Option[ContentType] = {
    contentTypeName match {
      case Some(contentType) ⇒ Some(ContentType(contentType))
      case None ⇒ None
    }
  }
}

case class ResourceConfig(traits: Traits, requests: Requests, responses: Responses)
object ResourceConfig {
  def apply(traits: Traits): ResourceConfig = {
    ResourceConfig(traits, Requests(Map()), Responses(Map()))
  }
}

case class Traits(commonTraits: Seq[Trait], methodSpecificTraits: Map[Method, Seq[Trait]])

case class Requests(dataStructures: Map[(Method, Option[ContentType]), DataStructure])

case class Responses(dataStructures: Map[(Method, Int), DataStructure])

case class Trait(name: String, parameters: Map[String, String])
object Trait {
  val STREAMED_RELIABLE = "streamed-reliable"
  val STREAMED_UNRELIABLE = "streamed-unreliable"
  val PLAIN_RESOURCE = "plain-resource"

  val EVENT_FEED_URI = "eventFeedUri"
  val RESOURCE_STATE_URI = "resourceStateUri"

  def apply(name: String): Trait = {
    Trait(name, Map())
  }
}

case class Method(name: String)
object Method {
  val POST = "post"
  val GET = "get"
}

case class ContentType(mediaType: String)

case class DataStructure(headers: Seq[Header], body: Option[Body])

case class Header(name: String)

case class DataType(typeName: String, fields: Seq[Field], annotations: Seq[Annotation])
object DataType {
  def apply(): DataType = {
    DataType(DEFAULT_TYPE_NAME, Seq(), Seq())
  }
  val DEFAULT_TYPE_NAME = "string"
}

case class Body(dataType: DataType)

case class Field(name: String, dataType: DataType) {
  def isPrivate: Boolean = dataType.annotations.exists(_.name == Annotation.PRIVATE)
}

case class Annotation(name: String, value: Option[RamlAnnotation])

object Annotation {
  val PRIVATE = "privateField"
  val CLIENT_LANGUAGE = "x-client-language"
  val CLIENT_IP = "x-client-ip"

  def apply(name: String): Annotation = Annotation(name, None)
}
