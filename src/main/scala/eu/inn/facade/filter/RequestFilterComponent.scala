package eu.inn.facade.filter

import eu.inn.facade.filter.RequestMapper._
import eu.inn.hyperbus.model.{Response, DynamicBody, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import spray.http.{HttpResponse, HttpRequest}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait RequestFilterComponent {
  this: FilterChainComponent ⇒

  lazy val requestFilter = new RequestFilter

  class RequestFilter {

    def filterRequest(httpRequest: HttpRequest)
                     (implicit ec: ExecutionContext): Either[HttpResponse, HttpRequest] = {
      var (dynamicRequestHeader, dynamicBody): (RequestHeader, DynamicBody) = null
      toDynamicRequest(httpRequest) match {
        case DynamicRequest(header, body) ⇒ {
          dynamicRequestHeader = header
          dynamicBody = body
        }
      }
      val requestHeaders = extractRequestHeaders(httpRequest.headers, dynamicRequestHeader)
      val filteredDynamicRequest = filterChain(httpRequest.uri).applyInputFilters(requestHeaders, dynamicBody)
      var filteredHttpResult: Either[HttpResponse, HttpRequest] = null
      filteredDynamicRequest onComplete {
        case Success((filteredHeaders, filteredDynamicBody)) ⇒ {
          if (filteredHeaders.hasResponseCode ) filteredHttpResult = Left(httpResponse403)
          else {
            val filteredDynamicHeader = extractDynamicRequestHeader(filteredHeaders)
            val dynamicRequest = DynamicRequest(filteredDynamicHeader, filteredDynamicBody)
            filteredHttpResult = Right(toHttpRequest(dynamicRequest))
          }
        }
        case Failure(ex) ⇒ filteredHttpResult = Left(httpResponse500(ex))
      }
      filteredHttpResult
    }

    def filterResponse(response: HttpResponse): HttpResponse = ???

    def httpResponse403: HttpResponse = ???
    def httpResponse500(ex: Throwable): HttpResponse = ???
  }
}
