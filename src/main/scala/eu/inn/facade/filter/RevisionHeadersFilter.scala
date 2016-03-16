package eu.inn.facade.filter

import eu.inn.facade.model.{FacadeRequest, FacadeHeaders, FacadeResponse, ResponseFilter}
import eu.inn.hyperbus.model.Header
import scala.concurrent.{ExecutionContext, Future}

/**
  * This filter renames header "revision" to "hyperbus-revision"
  */
class RevisionHeadersFilter extends ResponseFilter {

  override def apply(request: FacadeRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.headers.get(Header.REVISION) match {
        case Some(revision) ⇒
          val revision = response.headers(Header.REVISION)
          response.copy(
            headers =
              (response.headers - Header.REVISION) + (FacadeHeaders.CLIENT_REVISION_ID → revision)
          )
        case _ ⇒ response
      }
    }
  }
}
