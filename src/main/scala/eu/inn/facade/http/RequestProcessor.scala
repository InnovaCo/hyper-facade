package eu.inn.facade.http

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
    beforeFilterChain.filterRequest(originalRequest, originalRequest) flatMap { r ⇒
      processRequestWithRaml(originalRequest, r, 0) flatMap { filteredRequest ⇒
        hyperbus <~ filteredRequest.toDynamicRequest recover {
          handleHyperbusExceptions(originalRequest)
        } flatMap { response ⇒
          ramlFilterChain.filterResponse(originalRequest, FacadeResponse(response)) flatMap { r ⇒
            afterFilterChain.filterResponse(originalRequest, r)
          }
        }
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      response
    }
  }

  def processRequestWithRaml(originalRequest: FacadeRequest, facadeRequest: FacadeRequest, tryNum: Int): Future[FacadeRequest] = {
    if (tryNum > maxRestarts) {
      Future.failed(
        new RestartLimitReachedException(tryNum, maxRestarts)
      )
    }
    else {
      ramlFilterChain.filterRequest(originalRequest, facadeRequest) recoverWith {
        case e : FilterRestartException ⇒
          if (log.isDebugEnabled) {
            log.debug(s"Request $originalRequest is restarted from $facadeRequest to ${e.request}")
          }
          processRequestWithRaml(originalRequest, e.request, tryNum + 1)
      }
    }
  }

  def handleHyperbusExceptions(originalRequest: FacadeRequest) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: HyperbusException[ErrorBody] ⇒
      hyperbusException

    case noRoute: NoTransportRouteException ⇒
      implicit val mcf = MessagingContextFactory.withCorrelationId(originalRequest.clientCorrelationId.getOrElse(IdGenerator.create()))
      val uri = spray.http.Uri(originalRequest.uri.pattern.specific)
      model.NotFound(ErrorBody("not_found", Some(s"'${uri.path}' is not found.")))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, originalRequest)
  }

  def handleFilterExceptions[T](originalRequest: FacadeRequest)(func: FacadeResponse ⇒ T) : PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        log.error(s"Request execution interrupted: $originalRequest", e)
      }
      func(e.response)

    case NonFatal(nonFatal) ⇒
      val response = handleInternalError(nonFatal, originalRequest)
      func(FacadeResponse(response))
  }

  def handleInternalError(exception: Throwable, originalRequest: FacadeRequest): Response[ErrorBody] = {
    implicit val mcf = MessagingContextFactory.withCorrelationId(originalRequest.clientCorrelationId.getOrElse(IdGenerator.create()))
    log.error(s"Exception while handling $originalRequest", exception)
    val errorId = IdGenerator.create()
    model.InternalServerError(ErrorBody("internal_server_error", Some(exception.getMessage), errorId = errorId))
  }
}
