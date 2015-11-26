package eu.inn.facade.filter

import eu.inn.facade.filter.model.{Headers, Header}
import eu.inn.hyperbus.model.DynamicBody
import spray.http.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

trait FilterChainComponent {

  def filterChain(uri: Uri): FilterChain = {
    new FilterChain(Seq(), Seq())
  }

  class FilterChain(val inputFilters: Seq[InputFilter], val outputFilters: Seq[OutputFilter]) {

    def applyInputFilters(originalHeaders: Headers, originalBody: DynamicBody): Future[(Headers, DynamicBody)] = {
      val accumulator: Future[(Headers, DynamicBody)] = Future {
        (originalHeaders, originalBody)
      }
      inputFilters.foldLeft(accumulator) { (previousResult, filter) ⇒
        val promise = Promise[(Headers, DynamicBody)]()
        previousResult.flatMap { result ⇒
          val (resultHeaders, resultBody) = result
          if (resultHeaders.hasResponseCode) Future(result)
          else filter.apply(resultHeaders, resultBody)
        } recoverWith {
          case t: Exception ⇒ promise.completeWith(errorResult(originalHeaders, originalBody)).future
        }
        promise.future
      }
    }

    def errorResult(headers: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      Future(headers withResponseCode Some(500), body)
    }

    def applyOutputFilters(headers: Headers, body: DynamicBody): (Headers, DynamicBody) = ???
  }

}
