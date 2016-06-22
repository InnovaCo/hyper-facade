package eu.inn.facade.filter.http

import akka.pattern.AskTimeoutException
import eu.inn.authentication._
import eu.inn.binders.value._
import eu.inn.facade.filter.model.RequestFilter
import eu.inn.facade.model._
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import eu.inn.hyperbus.{Hyperbus, IdGenerator}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationRequestFilter(implicit inj: Injector) extends RequestFilter with Injectable {

  val log = LoggerFactory.getLogger(getClass)
  val hyperbus = inject[Hyperbus]

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    contextWithRequest.request.headers.get(FacadeHeaders.AUTHORIZATION) match {
      case Some(credentials) ⇒
        val authRequest = AuthenticationRequest(AuthenticationRequestBody(credentials.head))
        hyperbus <~ authRequest recover {
          handleHyperbusExceptions(authRequest)
        } map { response: Response[Body] ⇒
          val authUserValue = response.body.asInstanceOf[DynamicBody].content.authUser
          val authUser = authUserValue.fromValue[AuthUser]
          val context = contextWithRequest.context
          val updatedContextStorage = context.contextStorage + (ContextStorage.AUTH_USER → authUser)
          contextWithRequest.copy(
            context = context.copy(
              contextStorage = updatedContextStorage
            )
          )
        }

      case None ⇒
        Future(contextWithRequest)
    }
  }

  def handleHyperbusExceptions(authRequest: AuthenticationRequest): PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: NotFound[ErrorBody] ⇒
      throw new FilterInterruptException(
        FacadeResponse(Unauthorized(ErrorBody("unauthorized"))),
        s"User with credentials ${authRequest.body.credentials} is not authorized!"
      )

    case hyperbusException: InternalServerError[ErrorBody] ⇒
      throw new FilterInterruptException(
        FacadeResponse(hyperbusException),
        s"Internal error in authorization service"
      )

    case noRoute: NoTransportRouteException ⇒
      NotFound(ErrorBody("not-found", Some(s"${authRequest.uri} is not found.")))

    case askTimeout: AskTimeoutException ⇒
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling $authRequest")
      GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${authRequest.uri}'"), errorId = errorId))
  }
}
