package eu.inn.facade.raml

class RamlConfig(val resourcesByUrl: Map[String, ResourceConfig]) {

  def traits(url: String, method: String): Seq[String] = {
    val traits = resourcesByUrl(url).traits
    traits.methodSpecificTraits
      .getOrElse(Method(method), traits.commonTraits)
      .map(foundTrait ⇒ foundTrait.name)
  }

  def requestDataStructure(url: String, method: String): DataStructure = {
    resourcesByUrl(url).requests.dataStructures(Method(method))
  }

  def responseDataStructure(url: String, method: String, statusCode: Int): DataStructure = {
    resourcesByUrl(url).responses.dataStructures((Method(method), statusCode))
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

case class Field(name: String, isPrivate: Boolean = false)
