package eu.inn.facade.filter.chain

import eu.inn.facade.model.{OutputFilter, TransitionalHeaders}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

class OutputFilterChain(val filters: Seq[OutputFilter]) {
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

object OutputFilterChain {
  def apply(filters: Seq[OutputFilter]): OutputFilterChain = {
    new OutputFilterChain(filters)
  }

  def apply(): OutputFilterChain = {
    new OutputFilterChain(Seq.empty)
  }
}