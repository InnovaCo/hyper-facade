package eu.inn.facade.http

import akka.pattern.AskTimeoutException
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import eu.inn.facade.utils.FutureUtils
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import eu.inn.hyperbus.{Hyperbus, IdGenerator, model}
import org.slf4j.Logger
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class FacadeRequestWithContext(
                                   context: FacadeRequestContext,
                                   request: FacadeRequest
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

  def processRequestToFacade(requestContext: FacadeRequestContext, request: FacadeRequest): Future[FacadeResponse] = {
    beforeFilterChain.filterRequest(requestContext, request) flatMap { unpreparedRequest ⇒
      val fctX = prepareContextAndRequestBeforeRaml(requestContext, unpreparedRequest)
      processRequestWithRaml(fctX) flatMap { fct ⇒
        hyperbus <~ fct.request.toDynamicRequest recover {
          handleHyperbusExceptions(fct.context)
        } flatMap { response ⇒
          FutureUtils.chain(FacadeResponse(response), fct.context.stages.map { stage ⇒
            ramlFilterChain.filterResponse(fct.context, stage, _ : FacadeResponse)
          }) flatMap { r ⇒
            afterFilterChain.filterResponse(fct.context, fct.context.stages.head, r)
          }
        }
      }
    } recover handleFilterExceptions(requestContext) { response ⇒
      response
    }
  }

  def processRequestWithRaml(fct: FCT): Future[FCT] = {
    if (fct.context.stages.size > maxRestarts) {
      Future.failed(
        new RestartLimitReachedException(fct.context.stages.size, maxRestarts)
      )
    }
    else {
      ramlFilterChain.filterRequest(fct.context, fct.request) map { filteredRequest ⇒
        FCT(fct.context, filteredRequest)
      } recoverWith {
        case e: FilterRestartException ⇒
          if (log.isDebugEnabled) {
            log.debug(s"Request ${fct.context} is restarted from ${fct.request} to ${e.facadeRequest}")
          }
          val templatedRequest = withTemplatedUri(e.facadeRequest)
          val fctNew = FCT(fct.context.withNextStage(templatedRequest), templatedRequest)
          processRequestWithRaml(fctNew)
      }
    }
  }


  /*def processEventWithRaml(requestContext: FacadeRequestContext, facadeRequest: FacadeRequest, tryNum: Int): Future[FacadeRequest] = {
    if (tryNum > maxRestarts) {
      Future.failed(
        new RestartLimitReachedException(tryNum, maxRestarts)
      )
    }
    else {
      ramlFilterChain.filterEvent(requestContext, facadeRequest) recoverWith {
        case e : FilterRestartException ⇒
          if (log.isDebugEnabled) {
            log.debug(s"Event $requestContext is restarted from $facadeRequest to ${e.facadeRequest}")
          }
          val templatedEvent = withTemplatedUri(e.facadeRequest)
          processEventWithRaml(requestContext, templatedEvent, tryNum + 1)
      }
    }
  }*/

  def prepareContextAndRequestBeforeRaml(requestContext: FacadeRequestContext, request: FacadeRequest) = {
    val ramlParsedUri = ramlConfig.resourceUri(request.uri.pattern.specific)
    val facadeRequestWithRamlUri = request.copy(uri = ramlParsedUri)
    val preparedContext = requestContext.withNextStage(facadeRequestWithRamlUri)
    FCT(preparedContext, facadeRequestWithRamlUri)
  }

  def withTemplatedUri(request: FacadeRequest): FacadeRequest = {
    val ramlParsedUri = ramlConfig.resourceUri(request.uri.pattern.specific)
    request.copy(uri = ramlParsedUri)
  }

  def handleHyperbusExceptions(requestContext: FacadeRequestContext) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: HyperbusException[ErrorBody] ⇒
      hyperbusException

    case noRoute: NoTransportRouteException ⇒
      implicit val mcf = requestContext.clientMessagingContext()
      model.NotFound(ErrorBody("not-found", Some(s"'${requestContext.pathAndQuery}' is not found.")))

    case askTimeout: AskTimeoutException ⇒
      implicit val mcf = requestContext.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling $requestContext")
      model.GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${requestContext.pathAndQuery}'"), errorId = errorId))

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
