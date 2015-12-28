package eu.inn.facade.raml

case class ResourceConfig(traits: Traits)

case class Traits(commonTraits: Set[Trait], methodSpecificTraits: Map[Method, Set[Trait]])

case class Trait(name: String)

case class Method(name: String)
