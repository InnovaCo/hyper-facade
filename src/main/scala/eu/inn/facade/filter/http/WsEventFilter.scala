package eu.inn.facade.filter.http

import com.typesafe.config.Config
import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class WsEventFilter(config: Config) extends OutputFilter(config) with EventFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      filterMessage(context, request).asInstanceOf[FacadeRequest]
    }
  }
}
