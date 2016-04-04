package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.raml.{ContentType, Method, RamlConfig, ResourceMethod}

class RamlFilterChain(ramlConfig: RamlConfig) extends FilterChain {

  def findRequestFilters(context: FacadeRequestContext, request: FacadeRequest): Seq[RequestFilter] = {
    requestOrEventFilters(request.uri.pattern.specific, request.method, request.contentType).requestFilters
  }

  def findResponseFilters(context: FacadeRequestContext, response: FacadeResponse): Seq[ResponseFilter] = {
    context.prepared match {
      case Some(r) ⇒
        val method = r.requestMethod
        val result = filtersOrMethod(r.requestUri.pattern.specific, method) match {
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
      case None ⇒
        Seq.empty
    }
  }

  def findEventFilters(context: FacadeRequestContext, event: FacadeRequest): Seq[EventFilter] = {
    context.prepared match {
      case Some(r) ⇒
        val uri = r.requestUri.pattern.specific
        val methodName = if (event.method.startsWith("feed:")) event.method.substring(5) else event.method
        requestOrEventFilters(uri, methodName, event.contentType).eventFilters

      case None ⇒
        Seq.empty
    }
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
