package eu.inn.facade.model

import eu.inn.authentication.AuthUser

object ContextStorage {
  val AUTH_USER = "authUser"
  val IS_AUTHORIZED = "isAuthorized"

  implicit class ExtendFacadeRequestContext(facadeRequestContext: FacadeRequestContext) {

    def authUser: Option[AuthUser] = {
      facadeRequestContext.contextStorage.get(AUTH_USER) match {
        case Some(user : AuthUser) ⇒ Some(user)
        case _ ⇒ None
      }
    }

    def isAuthorized: Boolean = {
      facadeRequestContext.contextStorage.get(IS_AUTHORIZED) match {
        case Some(authorized: Boolean) ⇒ authorized
        case None ⇒ false
      }
    }
  }
}
