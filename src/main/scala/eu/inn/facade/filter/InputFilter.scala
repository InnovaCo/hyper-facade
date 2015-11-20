package eu.inn.facade.filter

import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.serialization.{RequestHeader, ResponseHeader}

trait InputFilter {

  def apply(requestHeaders: Seq[RequestHeader], body: DynamicBody): (Either[Seq[RequestHeader], Seq[ResponseHeader]], DynamicBody)
}
