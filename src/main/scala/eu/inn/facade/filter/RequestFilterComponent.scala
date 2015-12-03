package eu.inn.facade.filter

import eu.inn.facade.filter.RequestMapper._
import eu.inn.facade.filter.chain.FilterChainComponent
import eu.inn.hyperbus.model.{Response, DynamicBody, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import spray.http.{HttpResponse, HttpRequest}

import scala.concurrent.{Promise, Future, ExecutionContext}
import scala.util.{Failure, Success}

trait RequestFilterComponent {
  this: FilterChainComponent ⇒
//
//  lazy val requestFilter = new RequestFilter
//
//  class RequestFilter {
//
//    def filterRequest(httpRequest: HttpRequest)
//                     (implicit ec: ExecutionContext): Future[Either[HttpResponse, HttpRequest]] = {
//      var (dynamicRequestHeader, dynamicBody): (RequestHeader, DynamicBody) = null
//      toDynamicRequest(httpRequest) match {
//        case DynamicRequest(header, body) ⇒ {
//          dynamicRequestHeader = header
//          dynamicBody = body
//        }
//      }
//      val httpHeaders = httpRequest.headers
//      val requestHeaders = extractRequestHeaders(httpHeaders, dynamicRequestHeader)
//      val filteredDynamicRequest = filterChain(httpRequest.uri).applyInputFilters(requestHeaders, dynamicBody)
//      val promisedResult = Promise[Either[HttpResponse, HttpRequest]]()
//      filteredDynamicRequest onComplete {
//        case Success((filteredHeaders, filteredDynamicBody)) ⇒ {
//          filteredHeaders.responseCode match {
//            case Some(code) ⇒ promisedResult.completeWith(Future(Left(httpResponse(code))))
//            case None ⇒ {
//              val filteredDynamicHeader = extractDynamicRequestHeader(filteredHeaders)
//              val dynamicRequest = DynamicRequest(filteredDynamicHeader, filteredDynamicBody)
//              promisedResult.completeWith(Future(Right(toHttpRequest(httpHeaders, dynamicRequest))))
//            }
//          }
//        }
//        case Failure(ex) ⇒ promisedResult.completeWith(Future(Left(httpResponse(500, ex))))
//      }
//      promisedResult.future
//    }
//
//    private def httpResponse(statusCode: Int): HttpResponse = ???
//    private def httpResponse(errorStatusCode:Int, ex: Throwable): HttpResponse = ???
//  }
}
