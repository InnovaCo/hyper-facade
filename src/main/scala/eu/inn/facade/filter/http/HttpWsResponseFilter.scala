package eu.inn.facade.filter.http

import com.typesafe.config.Config
import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config) extends OutputFilter(config) with ResponseFilter {
  override def apply(context: FacadeRequestContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      filterMessage(context, response).asInstanceOf[FacadeResponse]
    }
  }
}
