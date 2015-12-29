package eu.inn.facade.raml

case class ResourceConfig(traits: Traits, requests: Requests, responses: Responses)
object ResourceConfig {
  def apply(traits: Traits): ResourceConfig = {
    ResourceConfig(traits, Requests(Map()), Responses(Map()))
  }
}

case class Traits(commonTraits: Set[Trait], methodSpecificTraits: Map[Method, Set[Trait]])

case class Requests(dataTypes: Map[Method, DataType])

case class Responses(dataTypes: Map[(Method, Int), DataType])

case class Trait(name: String)

case class Method(name: String)

case class DataType(headers: Seq[Header], body: Body)
object DataType {
  def apply(): DataType = {
    DataType(Seq(), Body(Seq()))
  }
}

case class Header(name: String)

case class Body(fields: Seq[Field])

case class Field(name: String, isPrivate: Boolean)
