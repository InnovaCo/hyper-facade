package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.model.{Filter, Headers}
import eu.inn.hyperbus.model.DynamicBody
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Future
import scala.util.{Failure, Success}

import scala.concurrent.ExecutionContext.Implicits.global

class FilterChainTest extends FreeSpec with Matchers with ScalaFutures with FilterChainComponent {
  val filterChain: FilterChain = new FilterChain(Seq(new TestInputFilter), Seq(new TestOutputFilter))

  class TestInputFilter extends Filter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (requestHeaders.headers.nonEmpty) Future(requestHeaders, body)
      else Future(requestHeaders withResponseCode Some(403), DynamicBody(Text("Forbidden")))
    }
  }

  class TestFailedFilter extends Filter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      throw new FilterNotPassedException(403, "Forbidden")
    }
  }

  class TestOutputFilter extends Filter {
    override def apply(responseHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (responseHeaders.headers.nonEmpty) Future(responseHeaders, body)
      else Future(Headers(Map("x-http-header" → "Accept-Language"), Some(200)), null)
    }
  }

  "FilterChain " - {
    "applyInputFilters empty headers" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(Map())

      val filteringResult = filterChain.applyInputFilters(requestHeaders, body)
      whenReady(filteringResult) {
        case Success((filteredHeaders, filteredBody)) ⇒
          filteredBody shouldBe DynamicBody(Text("Forbidden"))
          filteredHeaders.headers shouldBe Map()
          filteredHeaders.statusCode shouldBe Some(403)
        case Failure(error) ⇒ fail(error)
      }
    }

    "applyInputFilters non empty headers" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(
        Map("url" → "/some_url", "messageId" → "#12345", "correlationId" → "#54321")
      )

      val filteringResult = filterChain.applyInputFilters(requestHeaders, body)
      whenReady(filteringResult) {
        case Success((filteredHeaders, filteredBody)) ⇒
          filteredBody shouldBe DynamicBody(Text("test body"))
          filteredHeaders.headers shouldBe Map("url" → "/some_url", "messageId" → "#12345", "correlationId" → "#54321")
          filteredHeaders.statusCode shouldBe None
        case Failure(error) ⇒ fail(error)
      }
    }

    "applyInputFilters with error" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(Map())

      val failedFilterChain = new FilterChain(Seq(new TestFailedFilter, new TestInputFilter), Seq())
      val filteringResult = failedFilterChain.applyInputFilters(requestHeaders, body)
      whenReady(filteringResult) {
        case Success((filteredHeaders, filteredBody)) ⇒ fail("this filter chain should not be processed without errors")
        case e @ Failure(FilterNotPassedException(responseCode, message)) ⇒
          println("here")
          responseCode shouldBe 403
          message shouldBe "Forbidden"
        case _ ⇒ fail("Wrong exception type")
      }
    }

    "applyOutputFilters empty headers" in {
      val body = DynamicBody(Text("test body"))
      val responseHeaders = Headers(Map())

      val filteringResult = filterChain.applyOutputFilters(responseHeaders, body)
      whenReady(filteringResult) {
        case Success((filteredHeaders, filteredBody)) ⇒
          filteredBody shouldBe null
          filteredHeaders.headers shouldBe Map("x-http-header" → "Accept-Language")
          filteredHeaders.statusCode shouldBe Some(200)
        case Failure(error) ⇒ fail(error)
      }
    }

    "applyOutputFilters non empty headers" in {
      val body = DynamicBody(Text("test body"))
      val responseHeaders = Headers(
        Map("contentType" → "application/json", "messageId" → "#12345", "correlationId" → "#54321"),
        Some(200)
      )

      val filteringResult = filterChain.applyOutputFilters(responseHeaders, body)
      whenReady(filteringResult) {
        case Success((filteredHeaders, filteredBody)) ⇒
          filteredBody shouldBe DynamicBody(Text("test body"))
          filteredHeaders.headers shouldBe Map("contentType" → "application/json", "messageId" → "#12345", "correlationId" → "#54321")
          filteredHeaders.statusCode shouldBe Some(200)
        case Failure(error) ⇒ fail(error)
      }
    }

    "applyOutputFilters with error" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(Map())

      val failedFilterChain = new FilterChain(Seq(), Seq(new TestFailedFilter, new TestInputFilter))
      val filteringResult = failedFilterChain.applyOutputFilters(requestHeaders, body)
      whenReady(filteringResult) {
        case Success((filteredHeaders, filteredBody)) ⇒ fail("this filter chain should not be processed without errors")
        case e @ Failure(FilterNotPassedException(responseCode, message)) ⇒
          println("here")
          responseCode shouldBe 403
          message shouldBe "Forbidden"
        case _ ⇒ fail("Wrong exception type")
      }
    }
  }
}
