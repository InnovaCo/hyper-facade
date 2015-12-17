package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.model.{Headers, Filter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class PrivateResourceFilter extends Filter {
  override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future(Headers(Some(403)), DynamicBody(Text("Forbidden")))
  }
}
