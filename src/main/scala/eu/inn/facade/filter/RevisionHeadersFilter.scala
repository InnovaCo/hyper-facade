package eu.inn.facade.filter

import eu.inn.facade.model.{FacadeRequest, FacadeHeaders, FacadeResponse, ResponseFilter}
import eu.inn.hyperbus.model.Header
import scala.concurrent.{ExecutionContext, Future}

/**
  * This filter renames header "revision" to "hyperbus-revision"
  * todo: replace with def input/output filter
  */
class RevisionHeadersFilter extends ResponseFilter {

  override def apply(originalRequest: FacadeRequest, originalResponse: FacadeResponse, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.headers.get(Header.REVISION) match {
        case Some(revision) ⇒
          val revision = response.headers(Header.REVISION)
          response.copy(
            headers =
              (response.headers - Header.REVISION) + (FacadeHeaders.CLIENT_REVISION → revision)
          )
        case _ ⇒ response
      }
    }
  }
}
