package eu.inn.facade.filter

import eu.inn.facade.model.{FacadeHeaders, OutputFilter, TransitionalHeaders}
import eu.inn.hyperbus.model.{DynamicBody, Header}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * This filter renames header "revision" to "hyperbus-revision"
  */
class RevisionHeadersFilter extends OutputFilter {

  override def apply(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    Future {
      val updatedHeaders = headers.headerOption(Header.REVISION) match {
        case Some(revision) ⇒
          val revision = headers.headers(Header.REVISION)
          (headers.headers - Header.REVISION) + (FacadeHeaders.CLIENT_REVISION_ID → revision)

        case None ⇒ headers.headers
      }
      (TransitionalHeaders(headers.uri, updatedHeaders, headers.statusCode), body)
    }
  }
}
