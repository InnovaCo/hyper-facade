package eu.inn.facade.model

import eu.inn.hyperbus.model.Header._

import scala.collection.Set

object FacadeHeaders {
  val CLIENT_REVISION = "hyperbus-revision"
  val CLIENT_MESSAGE_ID = "hyperbus-message-id"
  val CLIENT_CORRELATION_ID = "hyperbus-correlation-id"

  val CERTAIN_CONTENT_TYPE_START = "application/vnd."
  val CERTAIN_CONTENT_TYPE_END = "+json"
  val COMMON_CONTENT_TYPE = "application/json"
}
