package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.standard.Method
import eu.inn.hyperbus.serialization.{ResponseHeader, RequestHeader}
import org.scalatest.{Matchers, FreeSpec}

class FilterChainTest extends FreeSpec with Matchers {
  val filterChain: FilterChain = new FilterChain(Seq(new TestInputFilter), Seq(new TestOutputFilter))

  class TestInputFilter extends InputFilter {
    override def apply(requestHeaders: Seq[RequestHeader], body: DynamicBody): (Either[Seq[RequestHeader], Seq[ResponseHeader]], DynamicBody) = {
      if (requestHeaders nonEmpty) (Left(requestHeaders), body)
      else (Right(Seq(ResponseHeader(403, None, "testMessage", Some("correlationId")))), body)
    }
  }

  class TestOutputFilter extends OutputFilter {
    override def apply(responseHeaders: Seq[ResponseHeader], body: DynamicBody): (Seq[ResponseHeader], DynamicBody) = {
      if (responseHeaders nonEmpty) (responseHeaders, body)
      else (Seq(ResponseHeader(204, None, "testMessage", Some("correlationId"))), null)
    }
  }

  "FilterChain " - {
    "applyInputFilters empty headers" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Seq[RequestHeader]()

      val (filteredHeaders, filteredBody) = filterChain.applyInputFilters(requestHeaders, body)
      filteredHeaders.isRight shouldBe true
      filteredHeaders.right.get shouldBe Seq(
        ResponseHeader(403, None, "testMessage", Some("correlationId"))
      )
      filteredBody shouldBe DynamicBody(Text("test body"))
    }

    "applyInputFilters non empty headers" in {
      val body = DynamicBody(Text("test body"))
      val requestHeaders = Seq[RequestHeader](
        RequestHeader("/some_url", Method.GET, None, "testMessage", Some("correlationId"))
      )

      val (filteredHeaders, filteredBody) = filterChain.applyInputFilters(requestHeaders, body)
      filteredHeaders.isLeft shouldBe true
      filteredHeaders.left.get shouldBe Seq(
        RequestHeader("/some_url", Method.GET, None, "testMessage", Some("correlationId"))
      )
      filteredBody shouldBe DynamicBody(Text("test body"))
    }

    "applyOutputFilters empty headers" in {
      val body = DynamicBody(Text("test body"))
      val responseHeaders = Seq[ResponseHeader]()

      val (filteredHeaders, filteredBody) = filterChain.applyOutputFilters(responseHeaders, body)
      filteredHeaders shouldBe Seq(
        ResponseHeader(204, None, "testMessage", Some("correlationId"))
      )
      filteredBody shouldBe null
    }

    "applyOutputFilters non empty headers" in {
      val body = DynamicBody(Text("test body"))
      val responseHeaders = Seq[ResponseHeader](
        ResponseHeader(200, None, "testMessage", Some("correlationId"))
      )

      val (filteredHeaders, filteredBody) = filterChain.applyOutputFilters(responseHeaders, body)
      filteredHeaders shouldBe Seq(
        ResponseHeader(200, None, "testMessage", Some("correlationId"))
      )
      filteredBody shouldBe DynamicBody(Text("test body"))
    }
  }
}
