package eu.inn.facade.model

trait Filter

trait RamlFilterFactory {
  def createRequestFilter(target: RamlTarget): Option[RequestFilter]
  def createResponseFilter(target: RamlTarget): Option[ResponseFilter]
  def createEventFilter(target: RamlTarget): Option[ResponseFilter]
}

sealed trait RamlTarget
case class TargetResource(uri: String) extends RamlTarget
case class TargetMethod(uri: String, method: String) extends RamlTarget
case class TargetResponse(uri: String, code: Int) extends RamlTarget
//RequestBody
//ResponseBody
// todo: inner fields!
case class TargetTypeDeclaration(typeName: String, fields: Seq[String]) extends RamlTarget