package eu.inn.facade.raml

case class ResourceConfig(traits: Traits, requests: Requests, responses: Responses)
object ResourceConfig {
  def apply(traits: Traits): ResourceConfig = {
    ResourceConfig(traits, Requests(Map()), Responses(Map()))
  }
}

case class Traits(commonTraits: Set[Trait], methodSpecificTraits: Map[Method, Set[Trait]])

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
