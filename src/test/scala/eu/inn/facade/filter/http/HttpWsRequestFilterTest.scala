package eu.inn.facade.filter.http

import eu.inn.binders.value._
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.FacadeRequest
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.model.Link
import eu.inn.hyperbus.model.Links._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class HttpWsRequestFilterTest extends FreeSpec with Matchers with ScalaFutures  with Injectable with MockContext {

  implicit val injector = Injectors()
  val beforeFilters = inject[FilterChain]("beforeFilterChain")

  "HttpWsRequestFilterTest " - {
    "_links formatting" in {
      val request = FacadeRequest(Uri("/v3/test"), "post", Map.empty,
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/v3/test/{a}", "templated" → true),
            "some-other1" → ObjV("href" → "/v3/test/abc", "templated" → false),
            "some-other2" → ObjV("href" → "/v3/test/xyz"),
            "some-other3" → List(
              ObjV("href" → "/v3/test/abc1"),
              ObjV("href" → "/v333/test/abc2"),
              ObjV("href" → "/v3/test/{b}", "templated" → true)
            )
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = mockContext(request)
      val filteredRequest = beforeFilters.filterRequest(context, request).futureValue
      filteredRequest.uri shouldBe Uri("/test")

      val linksMap = filteredRequest.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/test/{a}", templated = true))
      linksMap("some-other1") shouldBe Left(Link(href="/test/abc", templated = false))
      linksMap("some-other2") shouldBe Left(Link(href="/test/xyz", templated = false))
      linksMap("some-other3") shouldBe Right(
        Seq(
          Link(href="/test/abc1", templated = false),
          Link(href="/v333/test/abc2", templated = false),
          Link(href="/test/{b}", templated = true))
      )
    }

    "_embedded/_links formatting (event)" in {
      val request = FacadeRequest(Uri("/v3/test"), "post", Map.empty,
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/v3/inner-test/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/v3/inner-test-x/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/v3/inner-test-y/567", "templated" → false)
                )
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/v333/test/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = mockContext(request)
      val filteredRequest = beforeFilters.filterRequest(context, request).futureValue
      filteredRequest.uri shouldBe Uri("/test")

      val linksMap = filteredRequest.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/v333/test/{a}", templated = true))

      val e = filteredRequest.body.asMap("_embedded")
      val x = e.asMap("x")
      val innerLinksMap = x.__links.fromValue[LinksMap]
      innerLinksMap("self") shouldBe Left(Link(href="/inner-test/{a}", templated = true))

      val y = e.asMap("y")
      y shouldBe LstV(
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/inner-test-x/{b}", "templated" → true)
          ),
          "b" → 123
        ),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/inner-test-y/567", "templated" → false)
          )
        )
      )
    }
  }
}
