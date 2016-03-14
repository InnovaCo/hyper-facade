package eu.inn.facade.filter.model

import eu.inn.hyperbus.model.Header._

import scala.collection.Set

object FacadeHeaders {

  val CERTAIN_CONTENT_TYPE_START = "application/vnd."
  val CERTAIN_CONTENT_TYPE_END = "+json"
  val COMMON_CONTENT_TYPE = "application/json"
  val CLIENT_REVISION_ID = "hyperbus-revision"

  val headerNames = Set(METHOD, CONTENT_TYPE, MESSAGE_ID, CORRELATION_ID, REVISION)

  def isDynamicHeader(headerName: String): Boolean = {
    headerNames.contains(headerName)
  }
}
