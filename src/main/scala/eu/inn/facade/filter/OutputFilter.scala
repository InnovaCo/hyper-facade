package eu.inn.facade.filter

import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.serialization.ResponseHeader

trait OutputFilter {

  def apply(responseHeaders: Seq[ResponseHeader], body: DynamicBody): (Seq[ResponseHeader], DynamicBody)
}
