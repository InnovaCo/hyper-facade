package eu.inn.facade.filter

import eu.inn.facade.filter.model.Headers
import eu.inn.hyperbus.model.DynamicRequest
import eu.inn.hyperbus.serialization.RequestHeader
import spray.http.{HttpHeader, HttpRequest}

object RequestMapper {

    def toDynamicRequest(httpRequest: HttpRequest): DynamicRequest = ???

    def toHttpRequest(dynamicRequest: DynamicRequest): HttpRequest = ???

    def extractRequestHeaders(httpHeaders: List[HttpHeader], dynamicRequestHeader: RequestHeader): Headers = ???

    def extractDynamicRequestHeader(headers: Headers): RequestHeader = ???
}
