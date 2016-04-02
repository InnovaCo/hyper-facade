package eu.inn.facade.http

import akka.pattern.AskTimeoutException
import eu.inn.facade.filter.FilterContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import eu.inn.hyperbus.{Hyperbus, IdGenerator, model}
import org.slf4j.Logger
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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

  def processRequestToFacade(originalRequest: FacadeRequest): Future[FacadeResponse] = {
    val context1 = beforeFilterChain.createFilterContext(originalRequest, originalRequest)
    beforeFilterChain.filterRequest(context1, originalRequest) flatMap { r ⇒
      val context2 = beforeFilterChain.createFilterContext(originalRequest, r)

      processRequestWithRaml(context2, r, 0) flatMap { filteredRequest ⇒
        hyperbus <~ filteredRequest.toDynamicRequest recover {
          handleHyperbusExceptions(context2)
        } flatMap { response ⇒
          ramlFilterChain.filterResponse(context2, FacadeResponse(response)) flatMap { r ⇒
            afterFilterChain.filterResponse(context2, r)
          }
        }
      }
    } recover handleFilterExceptions(context1) { response ⇒
      response
    }
  }

  def processRequestWithRaml(filterContext: FilterContext, facadeRequest: FacadeRequest, tryNum: Int): Future[FacadeRequest] = {
    if (tryNum > maxRestarts) {
      Future.failed(
        new RestartLimitReachedException(tryNum, maxRestarts)
      )
    }
    else {
      val ramlParsedUri = ramlConfig.resourceUri(facadeRequest.uri.pattern.specific)
      val facadeRequestWithRamlUri = facadeRequest.copy(uri = ramlParsedUri)
      ramlFilterChain.filterRequest(filterContext, facadeRequestWithRamlUri) recoverWith {
        case e : FilterRestartException ⇒
          if (log.isDebugEnabled) {
            log.debug(s"Request $filterContext is restarted from $facadeRequestWithRamlUri to ${e.request}")
          }
          processRequestWithRaml(filterContext, e.request, tryNum + 1)
      }
    }
  }

  def handleHyperbusExceptions(filterContext: FilterContext) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: HyperbusException[ErrorBody] ⇒
      hyperbusException

    case noRoute: NoTransportRouteException ⇒
      implicit val mcf = filterContext.clientMessagingContext()
      model.NotFound(ErrorBody("not-found", Some(s"'${filterContext.originalPath}' is not found.")))

    case askTimeout: AskTimeoutException ⇒
      implicit val mcf = filterContext.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling $filterContext")
      model.GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${filterContext.originalPath}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, filterContext)
  }

  def handleFilterExceptions[T](filterContext: FilterContext)(func: FacadeResponse ⇒ T) : PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        log.error(s"Request execution interrupted: $filterContext", e)
      }
      func(e.response)

    case NonFatal(nonFatal) ⇒
      val response = handleInternalError(nonFatal, filterContext)
      func(FacadeResponse(response))
  }

  def handleInternalError(exception: Throwable, filterContext: FilterContext): Response[ErrorBody] = {
    implicit val mcf = filterContext.clientMessagingContext()
    val errorId = IdGenerator.create()
    log.error(s"Exception #$errorId while handling $filterContext", exception)
    model.InternalServerError(ErrorBody("internal-server-error", Some(exception.getMessage), errorId = errorId))
  }
}
