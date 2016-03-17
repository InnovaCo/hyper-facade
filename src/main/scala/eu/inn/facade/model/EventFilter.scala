package eu.inn.facade.model

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

case class EventFilterContext(
                                  uri: Uri,
                                  requestHeaders: Map[String, Seq[String]],
                                  requestBody: Value,
                                  eventMethod: String,
                                  eventHeaders: Map[String, Seq[String]],
                                  eventBody: Value
                                )

trait EventFilter extends Filter {
  def apply(context: EventFilterContext, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
