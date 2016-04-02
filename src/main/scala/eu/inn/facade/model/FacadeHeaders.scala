package eu.inn.facade.model

import eu.inn.hyperbus.model.Header

object FacadeHeaders {
  val CONTENT_TYPE            = "Content-Type"
  val CLIENT_REVISION         = "Hyperbus-Revision"
  val CLIENT_MESSAGE_ID       = "Hyperbus-Message-Id"
  val CLIENT_CORRELATION_ID   = "Hyperbus-Correlation-Id"
  val CLIENT_IP               = "X-Forwarded-For"
  val CLIENT_LANGUAGE         = "Accept-Language"

  val CERTAIN_CONTENT_TYPE_START  = "application/vnd."
  val CERTAIN_CONTENT_TYPE_END    = "+json"
  val COMMON_CONTENT_TYPE         = "application/json"

  val directHeaderMapping = Seq(
    CLIENT_CORRELATION_ID → Header.CORRELATION_ID,
    CLIENT_MESSAGE_ID → Header.MESSAGE_ID,
    CLIENT_REVISION → Header.REVISION
  )

  def httpContentTypeToGeneric(httpContentType: Option[String]): Option[String] = {
    httpContentType.map(_.toLowerCase) match {
      case Some(v) if (v.startsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_START)
        && v.endsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_END)) ⇒
        val beginIndex = FacadeHeaders.CERTAIN_CONTENT_TYPE_START.length
        val endIndex = v.length - FacadeHeaders.CERTAIN_CONTENT_TYPE_END.length
        val r = v.substring(beginIndex, endIndex)
        if (r.isEmpty)
          None
        else
          Some(r)

      case _ ⇒ None // application/json is also empty contentType for hyperbus
    }
  }

  def genericContentTypeToHttp(contentType: Option[String]): Option[String] = {
    contentType.map{ value ⇒
      FacadeHeaders.CERTAIN_CONTENT_TYPE_START + value + FacadeHeaders.CERTAIN_CONTENT_TYPE_END
      // case None ⇒ Some("application/json") // todo: do we need this?
    }
  }
}
