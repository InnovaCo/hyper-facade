package eu.inn.facade.filter

import eu.inn.facade.filter.model.{Filter, Headers}
import eu.inn.hyperbus.model.DynamicBody
import spray.http.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}
import scala.language.postfixOps
import scala.util.{Success, Failure, Try}

trait FilterChainComponent {

  def filterChain(uri: Uri): FilterChain = {
    new FilterChain(Seq(), Seq())
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
      try {
        val filteredResult = filters.foldLeft(accumulator) { (previousResult, filter) ⇒
          previousResult.flatMap { result ⇒
            val (resultHeaders, resultBody) = result
            filter.apply(resultHeaders, resultBody)
          }
        }
        val wrappedFilteredResult = filteredResult.flatMap(result ⇒ Future(Success(result)))
        promisedResult.completeWith(wrappedFilteredResult)
      } catch {
        case error: Throwable ⇒ promisedResult.completeWith(Future(Failure(error)))
      }
      promisedResult.future
    }
  }
}
