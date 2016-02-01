package eu.inn.facade.raml

class RamlConfig(val resourcesByUrl: Map[String, ResourceConfig]) {
  import Trait._

  def traitNames(url: String, method: String): Seq[String] = {
    traits(url, method) map (foundTrait ⇒ foundTrait.name)
  }

  def isReliableEventFeed(url: String) = {
    traitNames(url, Method.POST).contains(STREAMED_RELIABLE)
  }

  def isUnreliableEventFeed(url: String) = {
    traitNames(url, Method.POST).contains(Trait.STREAMED_UNRELIABLE)
  }

  def resourceFeedUri(url: String): String = {
    val resourceTraits = traits(url, Method.POST)
    resourceTraits.find(resourceTrait ⇒ isFeed(resourceTrait.name)) match {
      case Some(feedTrait) ⇒ feedTrait.parameters(EVENT_FEED_URI)
      case None ⇒ url // todo: is it correct?
    }
  }

  def resourceStateUri(url: String): String = {
    val resourceTraits = traits(url, Method.POST)
    resourceTraits.find(resourceTrait ⇒ hasMappedUri(resourceTrait.name)) match {
      case Some(resourceStateTrait) ⇒ resourceStateTrait.parameters(RESOURCE_STATE_URI)
      case None ⇒ url // todo: is it correct?
    }
  }

  def requestDataStructure(url: String, method: String, contentType: Option[String]): Option[DataStructure] = {
    resourcesByUrl(url).requests.dataStructures.get(Method(method), getContentType(contentType))
  }

  def responseDataStructure(url: String, method: String, statusCode: Int): Option[DataStructure] = {
    resourcesByUrl(url).responses.dataStructures.get((Method(method), statusCode))
  }

  def responseDataStructures(url: String, method: String): Seq[DataStructure] = {
    val allStructures = resourcesByUrl(url).responses.dataStructures
    allStructures.foldLeft(Seq[DataStructure]()) { (structuresByMethod, kv) ⇒
      val (httpMethod, _) = kv._1
      val structure = kv._2
      if (httpMethod == Method(method)) structuresByMethod :+ structure
      else structuresByMethod
    }
  }

  private def traits(url: String, method: String): Seq[Trait] = {
    val traits = resourcesByUrl(url).traits
    traits.methodSpecificTraits
      .getOrElse(Method(method), traits.commonTraits)
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

  def hasMappedUri(traitName: String): Boolean = {
    traitName == STREAMED_RELIABLE ||
      traitName == STREAMED_UNRELIABLE ||
      traitName == PLAIN_RESOURCE
  }

  def isFeed(traitName: String): Boolean = {
    traitName == STREAMED_RELIABLE ||
      traitName == STREAMED_UNRELIABLE
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
  def isPrivate: Boolean = dataType.annotations.contains(Annotation(Annotation.PRIVATE))
}

case class Annotation(name: String)
object Annotation {
  val PRIVATE = "privateField"
  val CLIENT_LANGUAGE = "x-client-language"
  val CLIENT_IP = "x-client-ip"
}
