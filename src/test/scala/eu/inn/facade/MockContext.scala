package eu.inn.facade

import eu.inn.facade.model.{FacadeRequest, FacadeRequestContext}

trait MockContext {
  def mockContext(request: FacadeRequest) = FacadeRequestContext(
    "127.0.0.1", spray.http.Uri(request.uri.formatted), request.uri.formatted, request.method, request.headers, None
  )
}
