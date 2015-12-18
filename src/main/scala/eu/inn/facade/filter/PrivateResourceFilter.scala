package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.model.{Headers, InputFilter}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrivateResourceFilter extends InputFilter {
  override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    Future(Headers(Some(403)), DynamicBody(Text("Forbidden")))
  }
}
