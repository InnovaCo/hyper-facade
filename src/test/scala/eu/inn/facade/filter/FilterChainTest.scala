package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Null, Text}
import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.model._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class FilterChainTest extends FreeSpec with Matchers with ScalaFutures {

  val filterChain = new SimpleFilterChain(
    requestFilters = Seq(new TestRequestFilter),
    responseFilters = Seq(new TestResponseFilter)
  ) // todo: + test eventFilters

  class TestRequestFilter extends RequestFilter {
    override def  apply(context: RequestFilterContext, input: FacadeRequest)
             (implicit ec: ExecutionContext): Future[FacadeRequest] = {
      if (input.headers.nonEmpty) {
        Future(input)
      }
      else {
        Future.failed(new FilterInterruptException(
          response = FacadeResponse(403, Map.empty, Text("Forbidden")),
          message = "Forbidden by filter"
        ))
      }
    }
  }

  class TestResponseFilter extends ResponseFilter {
    override def apply(context: ResponseFilterContext, output: FacadeResponse)(implicit ec: ExecutionContext): Future[FacadeResponse] = {
      if (output.headers.nonEmpty) {
        Future(output)
      }
      else {
        Future.failed(new FilterInterruptException(
          response = FacadeResponse(200, Map("x-http-header" → Seq("Accept-Language")), Null),
          message = "Interrupted by filter"
        ))
      }
    }
  }

  "FilterChain " - {
    "applyInputFilters empty headers" in {
      val request = FacadeRequest(Uri("testUri"), "get", Map.empty, Text("test body"))

      val interrupt = intercept[FilterInterruptException] {
        filterChain.filterRequest(request, request).awaitFuture
      }

      interrupt.response.body shouldBe Text("Forbidden")
      interrupt.response.headers shouldBe Map.empty
      interrupt.response.status shouldBe 403
    }

    "applyInputFilters non empty headers" in {
      val request = FacadeRequest(Uri("testUri"), "get",
        Map("url" → Seq("/some_url"), "messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        Text("test body"))

      val filteredRequest = filterChain.filterRequest(request, request).futureValue

      filteredRequest.body shouldBe Text("test body")
      filteredRequest.headers shouldBe Map("url" → Seq("/some_url"), "messageId" → Seq("#12345"), "correlationId" → Seq("#54321"))
    }
  }

  "applyOutputFilters empty headers" in {
    val request = FacadeRequest(Uri("testUri"), "get", Map.empty, Null)
    val response = FacadeResponse(201, Map.empty, Text("test body"))

    val interrupt = intercept[FilterInterruptException] {
      filterChain.filterResponse(request,response).awaitFuture
    }

    interrupt.response.body shouldBe Null
    interrupt.response.headers shouldBe Map("x-http-header" → Seq("Accept-Language"))
    interrupt.response.status shouldBe 200
  }

  "applyOutputFilters non empty headers" in {
    val request = FacadeRequest(Uri("testUri"), "get", Map.empty, Null)
    val response = FacadeResponse(200,
      Map("contentType" → Seq("application/json"), "messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
      Text("test body")
    )

    val filteredResponse = filterChain.filterResponse(request, response).futureValue

    filteredResponse.body shouldBe Text("test body")
    filteredResponse.headers shouldBe Map("contentType" → Seq("application/json"), "messageId" → Seq("#12345"), "correlationId" → Seq("#54321"))
    filteredResponse.status shouldBe 200
  }

  implicit class TestAwait[T](future: Future[T]) {
    def awaitFuture: T = {
      Await.result(future, 10.seconds)
    }
  }
}

