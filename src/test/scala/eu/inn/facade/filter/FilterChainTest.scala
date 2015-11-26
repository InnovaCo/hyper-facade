package eu.inn.facade.filter

import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.OutputFilter
import eu.inn.facade.filter.model.{Headers, Header}
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.standard.Method
import org.scalatest.{Matchers, FreeSpec}

import scala.concurrent.Future

class FilterChainTest extends FreeSpec with Matchers with FilterChainComponent {
//  val filterChain: FilterChain = new FilterChain(Seq(new TestInputFilter), Seq(new TestOutputFilter))
//
//  class TestInputFilter extends InputFilter {
//    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
//      if (requestHeaders.headers.nonEmpty) Future(requestHeaders, body)
//      else Future(requestHeaders withResponseCode Some(503), body)
//    }
//  }
//
//  class TestOutputFilter extends OutputFilter {
//    override def apply(responseHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
//      if (responseHeaders.headers.nonEmpty) Future(responseHeaders, body)
//      else Future(Headers(Seq(), Some(204)), null)
//    }
//  }
//
//  "FilterChain " - {
//    "applyInputFilters empty headers" in {
//      val body = DynamicBody(Text("test body"))
//      val requestHeaders = Seq[Header]()
//
//      val (filteredHeaders, filteredBody) = filterChain.applyInputFilters(requestHeaders, body)
//      filteredHeaders.isRight shouldBe true
//      filteredHeaders.right.get shouldBe Seq(
//        ResponseHeader(403, None, "testMessage", Some("correlationId"))
//      )
//      filteredBody shouldBe DynamicBody(Text("test body"))
//    }
//
//    "applyInputFilters non empty headers" in {
//      val body = DynamicBody(Text("test body"))
//      val requestHeaders = Seq[Header](
//        Header("/some_url", Method.GET, None, "testMessage", Some("correlationId"))
//      )
//
//      val (filteredHeaders, filteredBody) = filterChain.applyInputFilters(requestHeaders, body)
//      filteredHeaders.isLeft shouldBe true
//      filteredHeaders.left.get shouldBe Seq(
//        Header("/some_url", Method.GET, None, "testMessage", Some("correlationId"))
//      )
//      filteredBody shouldBe DynamicBody(Text("test body"))
//    }
//
//    "applyOutputFilters empty headers" in {
//      val body = DynamicBody(Text("test body"))
//      val responseHeaders = Seq[ResponseHeader]()
//
//      val (filteredHeaders, filteredBody) = filterChain.applyOutputFilters(responseHeaders, body)
//      filteredHeaders shouldBe Seq(
//        ResponseHeader(204, None, "testMessage", Some("correlationId"))
//      )
//      filteredBody shouldBe null
//    }
//
//    "applyOutputFilters non empty headers" in {
//      val body = DynamicBody(Text("test body"))
//      val responseHeaders = Seq[ResponseHeader](
//        ResponseHeader(200, None, "testMessage", Some("correlationId"))
//      )
//
//      val (filteredHeaders, filteredBody) = filterChain.applyOutputFilters(responseHeaders, body)
//      filteredHeaders shouldBe Seq(
//        ResponseHeader(200, None, "testMessage", Some("correlationId"))
//      )
//      filteredBody shouldBe DynamicBody(Text("test body"))
//    }
//  }
}
