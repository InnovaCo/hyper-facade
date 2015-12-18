package eu.inn.facade.filter.chain

import eu.inn.facade.filter.model.{Filter, Headers}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

import scala.language.postfixOps

class FilterChain(val filters: Seq[Filter]) {

  def applyFilters(headers: Headers, body: DynamicBody): Future[Try[(Headers, DynamicBody)]] = {
    val accumulator: Future[(Headers, DynamicBody)] = Future {
      (headers, body)
    }
    val promisedResult = Promise[Try[(Headers, DynamicBody)]]()
    if (filters nonEmpty) {
      filters.foldLeft(accumulator) { (previousResult, filter) ⇒
        previousResult.flatMap { result ⇒
          val (resultHeaders, resultBody) = result
          filter.apply(resultHeaders, resultBody)
        }
      } onComplete { filteredResult ⇒ promisedResult.completeWith(Future(filteredResult)) }
    } else {
      promisedResult.completeWith(Future(Success((headers, body))))
    }
    promisedResult.future
  }
}

object FilterChain {
  def apply(filters: Seq[Filter]) = {
    new FilterChain(filters)
  }

  def apply() = {
    new FilterChain(Seq())
  }
}