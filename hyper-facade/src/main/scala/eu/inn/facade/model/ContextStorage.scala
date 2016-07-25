package eu.inn.facade.model

import eu.inn.facade.model.authentication.AuthUser

object ContextStorage {
  val AUTH_USER = "auth-user"
  val IS_AUTHORIZED = "is-authorized"

  implicit class ExtendFacadeRequestContext(facadeRequestContext: FacadeRequestContext) {

    def authUser: Option[AuthUser] = {
      facadeRequestContext.contextStorage.get(AUTH_USER) match {
        case Some(user : AuthUser) ⇒ Some(user)
        case _ ⇒ None
      }
    }
  }
}
