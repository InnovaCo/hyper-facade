package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.raml.{ContentType, Method, RamlConfig}
import scaldi.Injectable


class RamlFilterChain(ramlConfig: RamlConfig) extends FilterChain with Injectable {
  def requestFilters(context: RequestFilterContext, request: FacadeRequest): Seq[RequestFilter] = {
    ramlConfig.resourcesByUri.get(request.uri.formatted) match {
      case Some(resource) ⇒
        val methodFilters =
          resource.requests.dataStructures.get(
            (Method(request.method),None)
          ).map(_.filters.requestFilters).getOrElse(Seq.empty)

        val dataTypeFilters =
          resource.requests.dataStructures.get(
            (Method(request.method),request.contentType.map(ContentType))
          ).map(_.filters.requestFilters).getOrElse(Seq.empty)

        resource.filters.requestFilters ++ methodFilters ++ dataTypeFilters
      case None ⇒ Seq.empty
    }
  }

  def responseFilters(context: ResponseFilterContext, response: FacadeResponse): Seq[ResponseFilter] = {
    ramlConfig.resourcesByUri.get(context.uri.formatted) match {
      case Some(resource) ⇒
        val methodFilters =
          resource.requests.dataStructures.get(
            (Method(context.method),None)
          ).map(_.filters.responseFilters).getOrElse(Seq.empty)

        val dataTypeFilters =
          resource.responses.dataStructures.get(
            (Method(context.method), response.status)
          ).map(_.filters.responseFilters).getOrElse(Seq.empty)

        resource.filters.responseFilters ++ methodFilters ++ dataTypeFilters
      case None ⇒ Seq.empty
    }
  }

  def eventFilters(context: EventFilterContext, event: FacadeRequest): Seq[EventFilter] = {
    ramlConfig.resourcesByUri.get(context.uri.formatted) match {
      case Some(resource) ⇒
        val methodFilters =
          resource.requests.dataStructures.get(
            (Method(event.method),None)
          ).map(_.filters.eventFilters).getOrElse(Seq.empty)

        val dataTypeFilters =
          resource.requests.dataStructures.get(
            (Method(event.method), event.contentType.map(ContentType))
          ).map(_.filters.eventFilters).getOrElse(Seq.empty)

        resource.filters.eventFilters ++ methodFilters ++ dataTypeFilters
      case None ⇒ Seq.empty
    }
  }
}
