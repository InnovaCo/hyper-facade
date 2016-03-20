package eu.inn.facade.model

import eu.inn.hyperbus.model.Header

object FacadeHeaders {
  val CLIENT_REVISION         = "hyperbusRevision"
  val CLIENT_MESSAGE_ID       = "hyperbusMessageId"
  val CLIENT_CORRELATION_ID   = "hyperbusCorrelationId"

  val CERTAIN_CONTENT_TYPE_START  = "application/vnd."
  val CERTAIN_CONTENT_TYPE_END    = "+json"
  val COMMON_CONTENT_TYPE         = "application/json"

  val directHeaderMapping = Seq(
    CLIENT_CORRELATION_ID → Header.CORRELATION_ID,
    CLIENT_MESSAGE_ID → Header.MESSAGE_ID,
    CLIENT_REVISION → Header.REVISION
  )
}
