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
}

case class ResourceConfig(traits: Traits, requests: Requests, responses: Responses)
object ResourceConfig {
  def apply(traits: Traits): ResourceConfig = {
    ResourceConfig(traits, Requests(Map()), Responses(Map()))
  }
}

case class Traits(commonTraits: Seq[Trait], methodSpecificTraits: Map[Method, Seq[Trait]])

case class Requests(dataStructures: Map[Method, DataStructure])

case class Responses(dataStructures: Map[(Method, Int), DataStructure])

case class Trait(name: String)

case class Method(name: String)

case class DataStructure(headers: Seq[Header], body: Body)
object DataStructure {
  def apply(headers: Seq[Header]): DataStructure = {
    DataStructure(headers, Body(Seq()))
  }
}

case class Header(name: String)

case class Body(fields: Seq[Field])

case class Field(name: String, annotations: Seq[Annotation]) {
  def isPrivate: Boolean = annotations.contains(Annotation(Annotation.PRIVATE))
}

case class Annotation(name: String)
object Annotation {
  val PRIVATE = "privateField"
  val CLIENT_LANGUAGE = "x-client-language"
  val CLIENT_IP = "x-client-ip"
}
