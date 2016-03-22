package eu.inn.facade.filter.http

import eu.inn.facade.model._
import eu.inn.facade.utils.NamingUtils
import eu.inn.hyperbus.model.Header

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter extends ResponseFilter {

  override def apply(context: ResponseFilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val headersBuilder = Map.newBuilder[String, Seq[String]]
      response.headers.foreach {
        case (Header.CONTENT_TYPE, value :: tail) ⇒
          headersBuilder += FacadeHeaders.CONTENT_TYPE → Seq(
            FacadeHeaders.CERTAIN_CONTENT_TYPE_START + value + FacadeHeaders.CERTAIN_CONTENT_TYPE_END
          )

        case (k, v) ⇒
          if (HttpWsResponseFilter.directHyperbusToFacade.contains(k)) {
            headersBuilder += HttpWsResponseFilter.directHyperbusToFacade(k) → v
          }
      }

      response.copy(
        headers = headersBuilder.result()
      )
    }
  }
}

object HttpWsResponseFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}
