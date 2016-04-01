package eu.inn.facade.model

import eu.inn.binders.value.Value
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

case class RequestFilterContext(
                                 uri: Uri,
                                 method: String,
                                 headers: Map[String, Seq[String]],
                                 body: Value
                               )

trait RequestFilter extends Filter {
  def apply(context: RequestFilterContext, request: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
