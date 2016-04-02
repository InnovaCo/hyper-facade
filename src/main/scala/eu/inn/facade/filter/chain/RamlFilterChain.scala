package eu.inn.facade.filter.chain

import eu.inn.facade.filter.FilterContext
import eu.inn.facade.model._
import eu.inn.facade.raml.{ContentType, Method, RamlConfig, ResourceMethod}

class RamlFilterChain(ramlConfig: RamlConfig) extends FilterChain {

  def findRequestFilters(context: FilterContext, request: FacadeRequest): Seq[RequestFilter] = {
    requestOrEventFilters(request.uri.formatted, request.method, request.contentType).requestFilters
  }

  def findResponseFilters(context: FilterContext, response: FacadeResponse): Seq[ResponseFilter] = {
    val uri = context.requestUri.formatted
    val method = context.requestMethod
    val result = filtersOrMethod(uri, method) match {
      case Left(filters) ⇒
        filters

      case Right(resourceMethod) ⇒
        resourceMethod.responses.get(response.status) match {
          case Some(responseDataStructures) ⇒
            responseDataStructures.dataStructures.get(response.clientContentType.map(ContentType)) match { // todo: test this!
              case Some(responseDataStructure) ⇒
                responseDataStructure.filters
              case None ⇒
                resourceMethod.filters
            }
          case None ⇒
            resourceMethod.filters
        }
    }
    result.responseFilters
  }

  def findEventFilters(context: FilterContext, event: FacadeRequest): Seq[EventFilter] = {
    val uri = context.requestUri.formatted
    val methodName = if (event.method.startsWith("feed:")) event.method.substring(5) else event.method
    requestOrEventFilters(uri, methodName, event.contentType).eventFilters
  }

  private def requestOrEventFilters(uri: String, method: String, contentType: Option[String]): SimpleFilterChain = {
    filtersOrMethod(uri, method) match {
      case Left(filters) ⇒ filters
      case Right(resourceMethod) ⇒
        resourceMethod.requests.dataStructures.get(contentType.map(ContentType)) match {
          case Some(requestDataStructure) ⇒
            requestDataStructure.filters
          case None ⇒
            resourceMethod.filters
        }
    }
  }

  private def filtersOrMethod(uri: String, method: String): Either[SimpleFilterChain, ResourceMethod] = {
    ramlConfig.resourcesByUri.get(uri) match {
      case Some(resource) ⇒
        resource.methods.get(Method(method)) match {
          case Some(resourceMethod) ⇒
            Right(resourceMethod)
          case None ⇒
            Left(resource.filters)
        }
      case None ⇒
        Left(FilterChain.empty)
    }
  }
}
