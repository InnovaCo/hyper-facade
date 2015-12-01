package eu.inn.facade.filter

import spray.http.{HttpRequest, HttpResponse, StatusCodes}
import spray.routing.Directive0
import spray.routing.Directives._
import scala.concurrent.ExecutionContext.Implicits.global

trait FilteringDirective {
  this: RequestFilterComponent ⇒

//  def withFilteredRequest: Directive0 = {
//    var result: Either[HttpResponse, HttpRequest] = null
//    val proceed = mapRequest { originalRequest ⇒
//      requestFilter.filterRequest(originalRequest) match {
//        case Right(httpRequest) ⇒ result = Right(httpRequest)
//        case Left(httpResponse) ⇒ result = Left(httpResponse)
//      }
//      if (result.isRight) result.right.get
//      else originalRequest
//    }
//    if (result.isRight) proceed
//    else complete {result.left.get}
//  }
}
