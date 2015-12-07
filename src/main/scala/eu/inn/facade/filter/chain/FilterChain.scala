package eu.inn.facade.filter.chain

import eu.inn.facade.filter.model.{Filter, Headers}
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.Try

object FilterChain {
  def apply(inputFilters: Seq[Filter], outputFilters: Seq[Filter]) = {
    new FilterChain(inputFilters, outputFilters)
  }
  
  def apply() = {
    new FilterChain(Seq(), Seq())
  }
}

class FilterChain(val inputFilters: Seq[Filter], val outputFilters: Seq[Filter]) {

  def applyInputFilters(headers: Headers, body: DynamicBody): Future[Try[(Headers, DynamicBody)]] = {
    applyFilters(inputFilters, headers, body)
  }

  def applyOutputFilters(headers: Headers, body: DynamicBody): Future[Try[(Headers, DynamicBody)]] = {
    applyFilters(outputFilters, headers, body)
  }

  private def applyFilters(filters: Seq[Filter], headers: Headers, body: DynamicBody): Future[Try[(Headers, DynamicBody)]] = {
    val accumulator: Future[(Headers, DynamicBody)] = Future {
      (headers, body)
    }
    val promisedResult = Promise[Try[(Headers, DynamicBody)]]()
    filters.foldLeft(accumulator) { (previousResult, filter) ⇒
      previousResult.flatMap { result ⇒
        val (resultHeaders, resultBody) = result
        filter.apply(resultHeaders, resultBody)
      }
    } onComplete { filteredResult ⇒ promisedResult.completeWith(Future(filteredResult)) }
    promisedResult.future
  }
}