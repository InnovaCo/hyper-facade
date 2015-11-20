package eu.inn.facade.filter

import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.serialization.{RequestHeader, ResponseHeader}

class FilterChain(val inputFilters: Seq[InputFilter], val outputFilters: Seq[OutputFilter]) {

  def applyInputFilters(headers: Seq[RequestHeader], body: DynamicBody): (Either[Seq[RequestHeader], Seq[ResponseHeader]], DynamicBody) = {
    val resultHeaders: Either[Seq[RequestHeader], Seq[ResponseHeader]] = Left(headers)
    inputFilters.foldLeft(resultHeaders, body) { (request, filter) ⇒
      request match {
        case (Left(headers), body) ⇒ filter.apply(headers, body)
        case (Right(headers), body) ⇒ (Right(headers), body)
      }
    }
  }

  def applyOutputFilters(headers: Seq[ResponseHeader], body: DynamicBody): (Seq[ResponseHeader], DynamicBody) = {
    outputFilters.foldLeft(headers, body) { (request, filter) ⇒
      filter.apply(request._1, request._2)
    }
  }
}
