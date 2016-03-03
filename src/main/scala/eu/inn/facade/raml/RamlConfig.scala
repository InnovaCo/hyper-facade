package eu.inn.facade.raml

class RamlConfig(val resourcesByUri: Map[String, ResourceConfig]) {
  import Trait._

  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method) map (foundTrait ⇒ foundTrait.name)
  }

  def isReliableEventFeed(uriPattern: String) = {
    traitNames(uriPattern, Method.POST).contains(STREAMED_RELIABLE)
  }

  def isUnreliableEventFeed(uriPattern: String) = {
    traitNames(uriPattern, Method.POST).contains(Trait.STREAMED_UNRELIABLE)
  }

  def resourceFeedUri(uriPattern: String): String = {
    val resourceTraits = traits(uriPattern, Method.POST)
    resourceTraits.find(resourceTrait ⇒ isFeed(resourceTrait.name)) match {
      case Some(feedTrait) ⇒ feedTrait.parameters(EVENT_FEED_URI)
      case None ⇒ uriPattern
    }
  }

  def resourceStateUri(uriPattern: String): String = {
    val resourceTraits = traits(uriPattern, Method.POST)
    resourceTraits.find(resourceTrait ⇒ hasMappedUri(resourceTrait.name)) match {
      case Some(resourceStateTrait) ⇒ resourceStateTrait.parameters(RESOURCE_STATE_URI)
      case None ⇒ uriPattern
    }
  }

  def requestDataStructure(uriPattern: String, method: String, contentType: Option[String]): Option[DataStructure] = {
    resourcesByUri(uriPattern).requests.dataStructures.get(Method(method), getContentType(contentType))
  }

  def responseDataStructure(uriPattern: String, method: String, statusCode: Int): Option[DataStructure] = {
    resourcesByUri(uriPattern).responses.dataStructures.get((Method(method), statusCode))
  }

  def responseDataStructures(uriPattern: String, method: String): Seq[DataStructure] = {
    val allStructures = resourcesByUri(uriPattern).responses.dataStructures
    allStructures.foldLeft(Seq[DataStructure]()) { (structuresByMethod, kv) ⇒
      val (httpMethod, _) = kv._1
      val structure = kv._2
      if (httpMethod == Method(method)) structuresByMethod :+ structure
      else structuresByMethod
    }
  }

  private def traits(uriPattern: String, method: String): Seq[Trait] = {
    resourcesByUri.get(uriPattern) match {
      case Some(config) ⇒
        val traits = config.traits
        traits.methodSpecificTraits
          .getOrElse(Method(method), traits.commonTraits)

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
