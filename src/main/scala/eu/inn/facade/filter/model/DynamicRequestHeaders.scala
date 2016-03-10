package eu.inn.facade.filter.model

import scala.collection.Set

object DynamicRequestHeaders {

  val METHOD = "method"
  val CONTENT_TYPE = "contentType"
  val MESSAGE_ID = "messageId"
  val CORRELATION_ID = "correlationId"
  val REVISION = "revision"

  val headerNames = Set(METHOD, CONTENT_TYPE, MESSAGE_ID, CORRELATION_ID)

  def isDynamicHeader(headerName: String): Boolean = {
    headerNames.contains(headerName)
  }
}
