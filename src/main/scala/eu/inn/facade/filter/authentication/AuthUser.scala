package eu.inn.facade.filter.authentication

import eu.inn.binders.value.Value
import eu.inn.facade.model.FacadeRequestContext

trait AuthUser {
  def id: String
  def roles: Set[String]
  def properties: Value
}

object AuthUser {
  val KEY_NAME = "auth-user"

  implicit class ExtendFacadeRequestContext(facadeRequestContext: FacadeRequestContext) {
    def authUser: Option[AuthUser] = {
      facadeRequestContext.prepared.get.contextStorage.get(KEY_NAME) match {
        case Some(user : AuthUser) ⇒ Some(user)
        case _ ⇒ None
      }
    }
  }
}