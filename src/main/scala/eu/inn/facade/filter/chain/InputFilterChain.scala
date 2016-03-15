package eu.inn.facade.filter.chain

import eu.inn.facade.model.{InputFilter, TransitionalHeaders}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

class InputFilterChain(val filters: Seq[InputFilter]) {

  def applyFilters(headers: TransitionalHeaders, body: DynamicBody): Future[(TransitionalHeaders, DynamicBody)] = {
    val accumulator: Future[(TransitionalHeaders, DynamicBody)] = Future {
      (headers, body)
    }
    val promisedResult = Promise[(TransitionalHeaders, DynamicBody)]()
    if (filters nonEmpty) {
      filters.foldLeft(accumulator) { (previousResult, filter) ⇒
        previousResult.flatMap { result ⇒
          val (resultHeaders, resultBody) = result
          filter.apply(resultHeaders, resultBody)
        }
      } onComplete { filteredResult ⇒ promisedResult.complete(filteredResult) }
    } else {
      promisedResult.completeWith(Future((headers, body)))
    }
    promisedResult.future
  }
}

object InputFilterChain {
  def apply(filters: Seq[InputFilter]) = {
    new InputFilterChain(filters)
  }

  def apply() = {
    new InputFilterChain(Seq.empty)
  }
}