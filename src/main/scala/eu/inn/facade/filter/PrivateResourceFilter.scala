package eu.inn.facade.filter

import java.io.ByteArrayOutputStream

import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.model.{Headers, InputFilter}
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.standard.ErrorBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrivateResourceFilter extends InputFilter {

  override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
    val error = new ByteArrayOutputStream()
    ErrorBody("Not Found").serialize(error)
    Future(Headers(Map(), Some(404)), DynamicBody(Text(error.toString("UTF-8"))))
  }
}
