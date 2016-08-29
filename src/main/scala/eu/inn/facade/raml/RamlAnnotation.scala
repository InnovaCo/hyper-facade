package eu.inn.facade.raml

import org.raml.v2.api.model.v10.datamodel.TypeInstanceProperty

trait RamlAnnotation {
  def name: String
  def predicate: Option[String]
}

object RamlAnnotation {
  val CLIENT_LANGUAGE = "x-client-language"
  val CLIENT_IP = "x-client-ip"
  val REWRITE = "rewrite"
  val DENY = "deny"
  val AUTHORIZE = "authorize"

  def apply(name: String, properties: Seq[TypeInstanceProperty]): RamlAnnotation = {
    val propMap = properties.map(property ⇒ property.name() → property.value.value().toString).toMap
    val predicate = propMap.get("if")
    name match {
      case DENY ⇒
        DenyAnnotation(predicate = predicate)
      case annotationName @ (CLIENT_IP | CLIENT_LANGUAGE) ⇒
        EnrichAnnotation(annotationName, predicate)
      case REWRITE ⇒
        RewriteAnnotation(predicate = predicate, uri = propMap("uri"))
      case annotationName ⇒
        RegularAnnotation(annotationName, predicate, propMap - "if")
    }
  }
}

case class RewriteAnnotation(name: String = RamlAnnotation.REWRITE,
                             predicate: Option[String],
                             uri: String) extends RamlAnnotation

case class DenyAnnotation(name: String = RamlAnnotation.DENY,
                         predicate: Option[String]) extends RamlAnnotation

case class EnrichAnnotation(name: String,
                            predicate: Option[String]) extends RamlAnnotation

case class AuthorizeAnnotation(name: String = RamlAnnotation.AUTHORIZE,
                               predicate: Option[String]) extends RamlAnnotation
case class RegularAnnotation(name: String, predicate: Option[String], properties: Map[String, String] = Map.empty) extends RamlAnnotation