package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.utils.FutureUtils

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(originalRequest: FacadeRequest, request: FacadeRequest)
                   (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val context = requestFilterContext(originalRequest)
    FutureUtils.chain(request, requestFilters(context, request).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def filterResponse(originalRequest: FacadeRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {

    val context = responseFilterContext(originalRequest, response)
    FutureUtils.chain(response, responseFilters(context, response).map(f ⇒ f.apply(context, _ : FacadeResponse)))
  }

  def filterEvent(originalRequest: FacadeRequest, event: FacadeRequest)
                 (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val context = eventFilterContext(originalRequest, event)
    FutureUtils.chain(event, eventFilters(context, event).map(f ⇒ f.apply(context, _ : FacadeRequest)))
  }

  def requestFilterContext(request: FacadeRequest) = {
    RequestFilterContext(
      request.uri,
      request.method,
      request.headers,
      request.body
    )
  }

  def responseFilterContext(request: FacadeRequest, response: FacadeResponse) = {
    ResponseFilterContext(
      request.uri,
      request.method,
      request.headers,
      request.body,
      response.headers,
      response.body
    )
  }

  def eventFilterContext(request: FacadeRequest, event: FacadeRequest) = {
    EventFilterContext(
      request.uri,
      request.headers,
      request.body,
      event.method,
      event.headers,
      event.body
    )
  }

  def requestFilters(context: RequestFilterContext, request: FacadeRequest): Seq[RequestFilter]
  def responseFilters(context: ResponseFilterContext, response: FacadeResponse): Seq[ResponseFilter]
  def eventFilters(context: EventFilterContext, event: FacadeRequest): Seq[EventFilter]
}
