package eu.inn.facade.http

import akka.pattern.AskTimeoutException
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.{FacadeRequestContext$, _}
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import eu.inn.hyperbus.{Hyperbus, IdGenerator, model}
import org.slf4j.Logger
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class FacadeRequestWithContext(
                                   request: FacadeRequest,
                                   context: FacadeRequestContext
                                   )

trait RequestProcessor extends Injectable {
  def log: Logger
  implicit def injector: Injector
  implicit def executionContext: ExecutionContext
  val hyperbus = inject[Hyperbus]
  val ramlConfig = inject[RamlConfig]
  val beforeFilterChain = inject[FilterChain]("beforeFilterChain")
  val ramlFilterChain = inject[FilterChain]("ramlFilterChain")
  val afterFilterChain = inject[FilterChain]("afterFilterChain")
  val maxRestarts = 5 // todo: move to config

  def processRequestToFacade(request: FacadeRequest, context: FacadeRequestContext): Future[FacadeResponse] = {
    beforeFilterChain.filterRequest(context, request) flatMap { r ⇒
      val preparedContext = context.prepare(r)

      processRequestWithRaml(preparedContext, r, 0) flatMap { filteredRequest ⇒
        hyperbus <~ filteredRequest.toDynamicRequest recover {
          handleHyperbusExceptions(preparedContext)
        } flatMap { response ⇒
          ramlFilterChain.filterResponse(preparedContext, FacadeResponse(response)) flatMap { r ⇒
            afterFilterChain.filterResponse(preparedContext, r)
          }
        }
      }
    } recover handleFilterExceptions(context) { response ⇒
      response
    }
  }

  def processRequestWithRaml(requestContext: FacadeRequestContext, facadeRequest: FacadeRequest, tryNum: Int): Future[FacadeRequest] = {
    if (tryNum > maxRestarts) {
      Future.failed(
        new RestartLimitReachedException(tryNum, maxRestarts)
      )
    }
    else {
      val ramlParsedUri = ramlConfig.resourceUri(facadeRequest.uri.pattern.specific)
      val facadeRequestWithRamlUri = facadeRequest.copy(uri = ramlParsedUri)
      ramlFilterChain.filterRequest(requestContext, facadeRequestWithRamlUri) recoverWith {
        case e : FilterRestartException ⇒
          if (log.isDebugEnabled) {
            log.debug(s"Request $requestContext is restarted from $facadeRequestWithRamlUri to ${e.request}")
          }
          processRequestWithRaml(requestContext, e.request, tryNum + 1)
      }
    }
  }

  def handleHyperbusExceptions(requestContext: FacadeRequestContext) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: HyperbusException[ErrorBody] ⇒
      hyperbusException

    case noRoute: NoTransportRouteException ⇒
      implicit val mcf = requestContext.clientMessagingContext()
      model.NotFound(ErrorBody("not-found", Some(s"'${requestContext.originalPath}' is not found.")))

    case askTimeout: AskTimeoutException ⇒
      implicit val mcf = requestContext.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling $requestContext")
      model.GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${requestContext.originalPath}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, requestContext)
  }

  def handleFilterExceptions[T](requestContext: FacadeRequestContext)(func: FacadeResponse ⇒ T) : PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        log.error(s"Request execution interrupted: $requestContext", e)
      }
      func(e.response)

    case NonFatal(nonFatal) ⇒
      val response = handleInternalError(nonFatal, requestContext)
      func(FacadeResponse(response))
  }

  def handleInternalError(exception: Throwable, requestContext: FacadeRequestContext): Response[ErrorBody] = {
    implicit val mcf = requestContext.clientMessagingContext()
    val errorId = IdGenerator.create()
    log.error(s"Exception #$errorId while handling $requestContext", exception)
    model.InternalServerError(ErrorBody("internal-server-error", Some(exception.getMessage), errorId = errorId))
  }
}
