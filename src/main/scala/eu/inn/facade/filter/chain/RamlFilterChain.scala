package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.raml.{ContentType, Method, RamlConfig, RamlResourceMethodConfig}

class RamlFilterChain(ramlConfig: RamlConfig) extends FilterChain {

  def findRequestFilters(request: FacadeRequest): Seq[RequestFilter] = {
    requestOrEventFilters(request.uri.pattern.specific, request.method, request.contentType).requestFilters
  }

  def findResponseFilters(preparedRequestContext: PreparedRequestContext, response: FacadeResponse): Seq[ResponseFilter] = {
    val method = preparedRequestContext.requestMethod
    val result = filtersOrMethod(preparedRequestContext.requestUri.pattern.specific, method) match {
      case Left(filters) ⇒
        filters

      case Right(resourceMethod) ⇒
        resourceMethod.responses.get(response.status) match {
          case Some(responses) ⇒
            responses.ramlContentTypes.get(response.clientContentType.map(ContentType)) match {
              // todo: test this!
              case Some(ramlContentType) ⇒
                ramlContentType.filters
              case None ⇒
                resourceMethod.methodFilters
            }
          case None ⇒
            resourceMethod.methodFilters
        }
    }
    result.responseFilters
  }

  def findEventFilters(preparedRequestContext: PreparedRequestContext, event: FacadeRequest): Seq[EventFilter] = {
    val uri = event.uri.pattern.specific
    val methodName = if (event.method.startsWith("feed:")) event.method.substring(5) else event.method
    requestOrEventFilters(uri, methodName, event.contentType).eventFilters
  }

  private def requestOrEventFilters(uri: String, method: String, contentType: Option[String]): SimpleFilterChain = {
    filtersOrMethod(uri, method) match {
      case Left(filters) ⇒ filters
      case Right(resourceMethod) ⇒
        resourceMethod.requests.ramlContentTypes.get(contentType.map(ContentType)) match {
          case Some(ramlContentType) ⇒
            ramlContentType.filters
          case None ⇒
            resourceMethod.methodFilters
        }
    }
  }

  private def filtersOrMethod(uri: String, method: String): Either[SimpleFilterChain, RamlResourceMethodConfig] = {
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
