package eu.inn.facade.model

import eu.inn.hyperbus.model.Header

object FacadeHeaders {
  val CONTENT_TYPE            = "Content-Type"
  val CLIENT_REVISION         = "Hyperbus-Revision"
  val CLIENT_MESSAGE_ID       = "Hyperbus-Message-Id"
  val CLIENT_CORRELATION_ID   = "Hyperbus-Correlation-Id"

  val CERTAIN_CONTENT_TYPE_START  = "application/vnd."
  val CERTAIN_CONTENT_TYPE_END    = "+json"
  val COMMON_CONTENT_TYPE         = "application/json"

  val directHeaderMapping = Seq(
    CLIENT_CORRELATION_ID → Header.CORRELATION_ID,
    CLIENT_MESSAGE_ID → Header.MESSAGE_ID,
    CLIENT_REVISION → Header.REVISION
  )
}
