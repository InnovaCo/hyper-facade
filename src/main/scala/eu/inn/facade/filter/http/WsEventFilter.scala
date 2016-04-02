package eu.inn.facade.filter.http

import eu.inn.facade.model.{FacadeRequestContext$, _}
import eu.inn.hyperbus.model.Header
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class WsEventFilter extends EventFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val headersBuilder = Map.newBuilder[String, Seq[String]]
      request.headers.foreach {
        case (Header.CONTENT_TYPE, value :: tail) ⇒
          headersBuilder += FacadeHeaders.CONTENT_TYPE → FacadeHeaders.genericContentTypeToHttp(Some(value)).toSeq

        case (k, v) ⇒
          if (WsEventFilter.directHyperbusToFacade.contains(k)) {
            headersBuilder += WsEventFilter.directHyperbusToFacade(k) → v
          }
      }

      request.copy(
        uri = Uri(request.uri.formatted),
        headers = headersBuilder.result()
      )
    }
  }
}

object WsEventFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}

