package eu.inn.facade.filter

import eu.inn.facade.model.{InputFilter, TransitionalHeaders}
import eu.inn.hyperbus.model.{DynamicBody, ErrorBody}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrivateResourceFilter extends InputFilter {

  override def apply(requestHeaders: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    Future(requestHeaders.withStatusCode(Some(404)), ErrorBody("not_found", Some("Resource not found")))
  }
}
