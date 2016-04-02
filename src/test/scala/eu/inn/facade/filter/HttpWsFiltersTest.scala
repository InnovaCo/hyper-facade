package eu.inn.facade.filter

import eu.inn.binders.value._
import eu.inn.facade.filter.chain.{FilterChain}
import eu.inn.facade.model._
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.model.Link
import eu.inn.hyperbus.model.Links.LinksMap
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class HttpWsFiltersTest extends FreeSpec with Matchers with ScalaFutures  with Injectable {

  implicit val injector = Injectors()
  val afterFilters = inject[FilterChain]("afterFilterChain")

  "HttpWsFiltersTest " - {
    "_links formatting" in {
      val request = FacadeRequest(Uri("/test"), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/test/{a}", "templated" → true),
            "some-other1" → ObjV("href" → "/test/abc", "templated" → false),
            "some-other2" → ObjV("href" → "/test/xyz"),
            "some-other3" → List(
              ObjV("href" → "/test/abc1"),
              ObjV("href" → "/test/abc2"),
              ObjV("href" → "/test/{b}", "templated" → true)
            )
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = RequestContext.create(request)
      val filteredResponse = afterFilters.filterResponse(context, response).futureValue
      val linksMap = filteredResponse.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/test/1"))
      linksMap("some-other1") shouldBe Left(Link(href="/test/abc"))
      linksMap("some-other2") shouldBe Left(Link(href="/test/xyz"))
      linksMap("some-other3") shouldBe Right(
        Seq(Link(href="/test/abc1"), Link(href="/test/abc2"), Link(href="/test/2"))
      )
    }

    "location header for 201" in {
      val request = FacadeRequest(Uri("/test"), "post", Map.empty, Null)
      val response = FacadeResponse(201, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/test/{a}", "templated" → true),
            "location" → ObjV("href" → "/test-factory/{b}", "templated" → true)
          ),
          "a" → 1,
          "b" → 100500
        )
      )

      val context = RequestContext.create(request)
      val filteredResponse = afterFilters.filterResponse(context, response).futureValue

      filteredResponse.headers("Location") shouldBe Seq("/test-factory/100500")
      val linksMap = filteredResponse.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/test/1"))
      linksMap("location") shouldBe Left(Link(href="/test-factory/100500"))
    }

    "_embedded/_links formatting" in {
      val request = FacadeRequest(Uri("/test"), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/inner-test/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/inner-test-x/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/inner-test-y/{c}", "templated" → true)
                ),
                "c" → 567
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/test/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = RequestContext.create(request)
      val filteredResponse = afterFilters.filterResponse(context, response).futureValue
      val linksMap = filteredResponse.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/test/1"))

      val e = filteredResponse.body.asMap("_embedded")
      val x = e.asMap("x")
      val innerLinksMap = x.__links.fromValue[LinksMap]
      innerLinksMap("self") shouldBe Left(Link(href="/inner-test/9"))

      val y = e.asMap("y")
      y shouldBe LstV(
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/inner-test-x/123")
          ),
          "b" → 123
        ),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/inner-test-y/567")
          ),
          "c" → 567
        )
      )
    }
  }
}
