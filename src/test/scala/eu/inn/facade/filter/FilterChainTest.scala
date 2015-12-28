package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.filter.model.{Filter, Headers, InputFilter, OutputFilter}
import eu.inn.hyperbus.model.DynamicBody
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class FilterChainTest extends FreeSpec with Matchers with ScalaFutures {
  val inputFilterChain: FilterChain = new FilterChain(Seq(new TestInputFilter))
  val outputFilterChain: FilterChain = new FilterChain(Seq(new TestOutputFilter))

  class TestInputFilter extends InputFilter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (requestHeaders.headers.nonEmpty) Future(requestHeaders, body)
      else Future(requestHeaders withResponseCode Some(403), DynamicBody(Text("Forbidden")))
    }
  }

  class TestFailedFilter extends Filter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      throw new FilterNotPassedException(403, "Forbidden")
    }

    override def isInputFilter: Boolean = true

    override def isOutputFilter: Boolean = true
  }

  class TestOutputFilter extends OutputFilter {
    override def apply(responseHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (responseHeaders.headers.nonEmpty) Future(responseHeaders, body)
      else Future(Headers(Map("x-http-header" → "Accept-Language"), Some(200)), null)
    }
  }

  "FilterChain " - {
    "applyInputFilters empty headers" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(Map[String, String](), None)

      val filteringResult = inputFilterChain.applyFilters(requestHeaders, body)
      whenReady(filteringResult) { filteredRequest ⇒
        val (filteredHeaders, filteredBody) = filteredRequest
        filteredBody shouldBe DynamicBody(Text("Forbidden"))
        filteredHeaders.headers shouldBe Map()
        filteredHeaders.statusCode shouldBe Some(403)
      }
    }

    "applyInputFilters non empty headers" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(Map("url" → "/some_url", "messageId" → "#12345", "correlationId" → "#54321"), None)

      val filteringResult = inputFilterChain.applyFilters(requestHeaders, body)
      whenReady(filteringResult) { filteredRequest ⇒
        val (filteredHeaders, filteredBody) = filteredRequest
        filteredBody shouldBe DynamicBody(Text("test body"))
        filteredHeaders.headers shouldBe Map("url" → "/some_url", "messageId" → "#12345", "correlationId" → "#54321")
        filteredHeaders.statusCode shouldBe None
      }
    }

    "applyInputFilters with error" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Headers(Map[String, String](), None)

      val failedInputFilterChain = new FilterChain(Seq(new TestFailedFilter, new TestInputFilter))
      val filteringResult = failedInputFilterChain.applyFilters(requestHeaders, body)
      whenReady(filteringResult.failed) {
        case FilterNotPassedException(responseCode, message) ⇒
          responseCode shouldBe 403
          message shouldBe "Forbidden"
        case ex: Throwable ⇒ fail("Wrong exception type" + ex)
      }
      filteringResult.value match {
        case Some(Success(value)) ⇒ fail("this filter chain should not be processed without errors")
        case Some(Failure(_)) ⇒
      }
    }
  }

  "applyOutputFilters empty headers" in {
    val body = DynamicBody(Text("test body"))
    val responseHeaders = Headers(Map[String, String](), None)

    val filteringResult = outputFilterChain.applyFilters(responseHeaders, body)
    whenReady(filteringResult) { filteredRequest ⇒
      val (filteredHeaders, filteredBody) = filteredRequest
      filteredBody shouldBe null
      filteredHeaders.headers shouldBe Map("x-http-header" → "Accept-Language")
      filteredHeaders.statusCode shouldBe Some(200)
    }
  }

  "applyOutputFilters non empty headers" in {
    val body = DynamicBody(Text("test body"))
    val responseHeaders = Headers(
      Map("contentType" → "application/json", "messageId" → "#12345", "correlationId" → "#54321"),
      Some(200)
    )

    val filteringResult = outputFilterChain.applyFilters(responseHeaders, body)
    whenReady(filteringResult) { filteredRequest ⇒
      val (filteredHeaders, filteredBody) = filteredRequest
      filteredBody shouldBe DynamicBody(Text("test body"))
      filteredHeaders.headers shouldBe Map("contentType" → "application/json", "messageId" → "#12345", "correlationId" → "#54321")
      filteredHeaders.statusCode shouldBe Some(200)
    }
  }

  "applyOutputFilters with error" in {
    val body = DynamicBody(Text("test body"))
    val requestHeaders = Headers(Map[String, String](), None)

    val failedOutputFilterChain = new FilterChain(Seq(new TestFailedFilter, new TestOutputFilter))
    val filteringResult = failedOutputFilterChain.applyFilters(requestHeaders, body)
    whenReady(filteringResult.failed) {
      case FilterNotPassedException(responseCode, message) ⇒
        responseCode shouldBe 403
        message shouldBe "Forbidden"
      case ex: Throwable ⇒ fail("Wrong exception type" + ex)
    }
    filteringResult.value match {
      case Some(Success(value)) ⇒ fail("this filter chain should not be processed without errors")
      case Some(Failure(_)) ⇒
    }
  }
}
