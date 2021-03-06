package eu.inn.facade.workers

import akka.pattern.AskTimeoutException
import com.typesafe.config.Config
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.metrics.MetricKeys
import eu.inn.facade.model._
import eu.inn.facade.raml.{RamlConfigurationReader, RamlStrictConfigException}
import eu.inn.facade.utils.FutureUtils
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import eu.inn.hyperbus.{Hyperbus, IdGenerator, model}
import eu.inn.metrics.Metrics
import org.slf4j.Logger
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait RequestProcessor extends Injectable {
  def log: Logger
  implicit def injector: Injector
  implicit def executionContext: ExecutionContext
  val hyperbus = inject[Hyperbus]
  val ramlConfig = inject[RamlConfigurationReader]
  val beforeFilterChain = inject[FilterChain]("beforeFilterChain")
  val ramlFilterChain = inject[FilterChain]("ramlFilterChain")
  val afterFilterChain = inject[FilterChain]("afterFilterChain")
  val config = inject[Config]
  val metrics = inject[Metrics]
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  def processRequestToFacade(cwr: ContextWithRequest): Future[FacadeResponse] = {
    metrics.timerOfFuture(MetricKeys.REQUEST_PROCESS_TIME) {
      beforeFilterChain.filterRequest(cwr) flatMap { unpreparedContextWithRequest ⇒
        val cwrBeforeRaml = prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest)
        processRequestWithRaml(cwrBeforeRaml) flatMap { cwrRaml ⇒
          hyperbus <~ cwrRaml.request.toDynamicRequest recover {
            handleHyperbusExceptions(cwrRaml)
          } flatMap { response ⇒
            FutureUtils.chain(FacadeResponse(response), cwrRaml.stages.map { _ ⇒
              ramlFilterChain.filterResponse(cwrRaml, _: FacadeResponse)
            }) flatMap { r ⇒
              afterFilterChain.filterResponse(cwrRaml, r)
            }
          }
        }
      } recover handleFilterExceptions(cwr) { response ⇒
        response
      }
    }
  }

  def processRequestWithRaml(cwr: ContextWithRequest): Future[ContextWithRequest] = {
    if (cwr.stages.size > rewriteCountLimit) {
      Future.failed(
        new RewriteLimitReachedException(cwr.stages.size, rewriteCountLimit)
      )
    }
    else {
      ramlFilterChain.filterRequest(cwr) flatMap { filteredCWR ⇒
        val filteredRequest = filteredCWR.request
        if (filteredRequest.uri.pattern == cwr.request.uri.pattern) {
          Future.successful(filteredCWR)
        } else {
          if (log.isDebugEnabled) {
            log.debug(s"Request ${cwr.context} is restarted from ${cwr.request} to $filteredRequest")
          }
          val templatedRequest = withTemplatedUri(filteredRequest)
          val cwrNext = cwr.withNextStage(templatedRequest)
          processRequestWithRaml(cwrNext)
        }
      }
    }
  }

  def prepareContextAndRequestBeforeRaml(cwr: ContextWithRequest) = {
    val request = cwr.request
    val ramlParsedUri = ramlConfig.resourceUri(request.uri, request.method)
    val facadeRequestWithRamlUri = request.copy(uri = ramlParsedUri)
    cwr.withNextStage(facadeRequestWithRamlUri)
  }

  def withTemplatedUri(request: FacadeRequest): FacadeRequest = {
    val ramlParsedUri = ramlConfig.resourceUri(request.uri, request.method)
    request.copy(uri = ramlParsedUri)
  }

  def handleHyperbusExceptions(cwr: ContextWithRequest) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: HyperbusException[ErrorBody] ⇒
      hyperbusException

    case _: NoTransportRouteException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      model.NotFound(ErrorBody("not-found", Some(s"'${cwr.context.pathAndQuery}' is not found.")))

    case _: AskTimeoutException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling ${cwr.context}")
      model.GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${cwr.context.pathAndQuery}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, cwr)
  }

  def handleFilterExceptions[T](cwr: ContextWithRequest)(func: FacadeResponse ⇒ T) : PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        log.error(s"Request execution interrupted: ${cwr.context}", e)
      }
      else if (log.isDebugEnabled) {
        log.debug(s"Request execution interrupted: ${cwr.context}", e)
      }
      func(e.response)

    case e: RamlStrictConfigException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.info(s"Exception #$errorId while handling ${cwr.context}", e)
      func(FacadeResponse(NotFound(ErrorBody("not-found", Some("Resource is not found"), errorId = errorId))))

    case NonFatal(nonFatal) ⇒
      val response = handleInternalError(nonFatal, cwr)
      func(FacadeResponse(response))
  }

  def handleInternalError(exception: Throwable, cwr: ContextWithRequest): Response[ErrorBody] = {
    implicit val mcf = cwr.context.clientMessagingContext()
    val errorId = IdGenerator.create()
    log.error(s"Exception #$errorId while handling ${cwr.context}", exception)
    model.InternalServerError(ErrorBody("internal-server-error", Some(exception.getClass.getName + ": " + exception.getMessage), errorId = errorId))
  }
}
