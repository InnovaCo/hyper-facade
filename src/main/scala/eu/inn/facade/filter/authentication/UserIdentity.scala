package eu.inn.facade.filter.authentication

import eu.inn.binders.value.Value
import eu.inn.facade.model.FacadeRequestContext

trait UserIdentity {
  def id: String
  def roles: Set[String]
  def properties: Value
}

object UserIdentity {
  val KEY_NAME = "user-identity"

  implicit class ExtendFacadeRequestContext(facadeRequestContext: FacadeRequestContext) {
    def userIdentity: Option[UserIdentity] = {
      facadeRequestContext.prepared.get.contextStorage.get(KEY_NAME) match {
        case Some(identity : UserIdentity) ⇒ Some(identity)
        case _ ⇒ None
      }
    }
  }
}