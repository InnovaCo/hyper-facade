package eu.inn.facade.http

import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.ErrorBody
import org.slf4j.Logger
import scaldi.{Injector, Injectable}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait RequestProcessor extends Injectable {
  def log: Logger
  implicit def injector: Injector
  implicit def executionContext: ExecutionContext
  val hyperBus = inject[HyperBus]
  val ramlConfig = inject[RamlConfig]
  val beforeFilterChain = inject[FilterChain]("beforeFilterChain")
  val ramlFilterChain = inject[FilterChain]("ramlFilterChain")
  val afterFilterChain = inject[FilterChain]("afterFilterChain")
  val maxRestarts = 5 // todo: move to config

  def processRequestToFacade(originalRequest: FacadeRequest): Future[FacadeResponse] = {
    val f = beforeFilterChain.filterRequest(originalRequest, originalRequest) flatMap { r ⇒
      processRequestWithRaml(originalRequest, r, 0) flatMap { filteredRequest ⇒
        hyperBus <~ filteredRequest.toDynamicRequest flatMap { response ⇒
          ramlFilterChain.filterResponse(originalRequest, FacadeResponse(response))
        }
      }
    }

    handleFilterExceptions(originalRequest, f) { response ⇒
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

  def handleFilterExceptions[T](originalRequest: FacadeRequest, future: Future[T])
                               (func: FacadeResponse ⇒ T): Future[T] = {
    future recover {
      case e: FilterInterruptException ⇒
        if (e.getCause != null) {
          log.error(s"Request execution interrupted: $originalRequest", e)
        }
        func(e.response)

      case NonFatal(e) ⇒
        val response = RequestMapper.exceptionToResponse(e)
        log.error(s"Service answered with error #${response.body.asInstanceOf[ErrorBody].errorId}. Stopping actor")
        func(FacadeResponse(response))
    }
  }
}
