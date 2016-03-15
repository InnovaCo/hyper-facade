package eu.inn.facade.filter

import java.io.ByteArrayOutputStream

import eu.inn.binders.dynamic.Text
import eu.inn.facade.model.{InputFilter, TransitionalHeaders}
import eu.inn.hyperbus.model.{DynamicBody, ErrorBody}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrivateResourceFilter extends InputFilter {

  override def apply(requestHeaders: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    val error = new ByteArrayOutputStream()
    ErrorBody("Not Found").serialize(error)
    Future(TransitionalHeaders(requestHeaders.uri, Map(), Some(404)), DynamicBody(Text(error.toString("UTF-8"))))
  }
}
