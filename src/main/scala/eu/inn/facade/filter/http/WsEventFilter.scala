package eu.inn.facade.filter.http

import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class WsEventFilter extends OutputFilter with EventFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      filterMessage(context, request).asInstanceOf[FacadeRequest]
    }
  }
}
