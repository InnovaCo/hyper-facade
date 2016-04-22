package eu.inn.facade

import eu.inn.facade.model.{FacadeRequest, FacadeRequestContext, PreparedRequestContext}

trait MockContext {
  def mockContext(request: FacadeRequest) = FacadeRequestContext(
    "127.0.0.1", spray.http.Uri(request.uri.formatted), request.uri.formatted, request.method, request.headers, Seq.empty
  ).prepareNext(request)

  def mockPreparedContext(request: FacadeRequest) = PreparedRequestContext(
    request.uri, request.method, request.headers
  )
}
