package eu.inn.facade.raml

class RamlConfig(val resourcesByUrl: Map[String, ResourceConfig]) {

  def traitNames(url: String, method: String): Seq[String] = {
    val traits = resourcesByUrl(url).traits
    traits.methodSpecificTraits
      .getOrElse(Method(method), traits.commonTraits)
      .map(foundTrait ⇒ foundTrait.name)
  }

  def requestDataStructure(url: String, method: String): Option[DataStructure] = {
    resourcesByUrl(url).requests.dataStructures.get(Method(method))
  }

  def responseDataStructure(url: String, method: String, statusCode: Int, contentType: Option[String]): Option[DataStructure] = {
    resourcesByUrl(url).responses.dataStructures.get((Method(method), statusCode, getContentType(contentType)))
  }

  def responseDataStructures(url: String, method: String, contentType: Option[String]): Seq[DataStructure] = {
    val allStructures = resourcesByUrl(url).responses.dataStructures
    allStructures.foldLeft(Seq[DataStructure]()) { (structuresByMethod, kv) ⇒
      val (httpMethod, _, ramlMediaType) = kv._1
      val structure = kv._2
      if ((httpMethod == Method(method)) && (ramlMediaType == contentType)) structuresByMethod :+ structure
      else structuresByMethod
    }
  }

  def getContentType(contentTypeName: Option[String]): Option[ContentType] = {
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

case class Requests(dataStructures: Map[Method, DataStructure])

case class Responses(dataStructures: Map[(Method, Int, Option[ContentType]), DataStructure])

case class Trait(name: String)

case class Method(name: String)

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
