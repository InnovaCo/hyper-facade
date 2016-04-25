package eu.inn.facade.filter.http

import eu.inn.binders.value._
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.{Method, RewriteIndexHolder}
import eu.inn.hyperbus.model.Link
import eu.inn.hyperbus.model.Links.LinksMap
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class HttpWsFiltersTest extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll with Injectable with MockContext {

  implicit val injector = Injectors()
  val afterFilters = inject[FilterChain]("afterFilterChain")

  override def beforeAll() = {
    RewriteIndexHolder.clearIndex()
    RewriteIndexHolder.updateRewriteIndex("/test", "/test-rewritten", None)
    RewriteIndexHolder.updateRewriteIndex("/test/{a}", "/test-rewritten/{a}", None)
    RewriteIndexHolder.updateRewriteIndex("/test/xyz", "/test-rewritten/xyz", None)
    RewriteIndexHolder.updateRewriteIndex("/inner-test/{a}", "/inner-test-rewritten/{a}", None)
  }

  "HttpWsFiltersTest " - {
    "_links rewriting and formatting (response)" in {
      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val response = FacadeResponse(200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
        "_links" → ObjV(
            "self" → ObjV("href" → "/test-rewritten/{a}", "templated" → true),
            "some-other1" → ObjV("href" → "/test-rewritten/abc", "templated" → false),
            "some-other2" → ObjV("href" → "/test-rewritten/xyz"),
            "some-other3" → List(
              ObjV("href" → "/test-rewritten/abc1"),
              ObjV("href" → "/test-rewritten/abc2"),
              ObjV("href" → "/test-rewritten/{b}", "templated" → true)
            )
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = mockContext(request)
      val filteredResponse = afterFilters.filterResponse(context, response).futureValue(Timeout(Span(300, Seconds)))
      val linksMap = filteredResponse.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/v3/test/1"))
      linksMap("some-other1") shouldBe Left(Link(href="/v3/test-rewritten/abc"))
      linksMap("some-other2") shouldBe Left(Link(href="/v3/test/xyz"))
      linksMap("some-other3") shouldBe Right(
        Seq(Link(href="/v3/test-rewritten/abc1"), Link(href="/v3/test-rewritten/abc2"), Link(href="/v3/test-rewritten/2"))
      )
    }

    "_links rewriting and formatting (event)" in {
      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val event = FacadeRequest(Uri("/test"), Method.POST, Map.empty,
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

      val context = mockContext(request)
      val filteredEvent = afterFilters.filterEvent(context, event).futureValue
      filteredEvent.uri shouldBe Uri("/v3/test")

      val linksMap = filteredEvent.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/v3/test/1"))
      linksMap("some-other1") shouldBe Left(Link(href="/v3/test/abc"))
      linksMap("some-other2") shouldBe Left(Link(href="/v3/test/xyz"))
      linksMap("some-other3") shouldBe Right(
        Seq(Link(href="/v3/test/abc1"), Link(href="/v3/test/abc2"), Link(href="/v3/test/2"))
      )
    }

    "location header for 201" in {
      val request = FacadeRequest(Uri("/test"), Method.POST, Map.empty, Null)
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

      val context = mockContext(request)
      val filteredResponse = afterFilters.filterResponse(context, response).futureValue

      filteredResponse.headers("Location") shouldBe Seq("/v3/test-factory/100500")
      val linksMap = filteredResponse.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/v3/test/1"))
      linksMap("location") shouldBe Left(Link(href="/v3/test-factory/100500"))
    }

    "_embedded/_links rewriting and formatting (response)" in {
      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val response = FacadeResponse(200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/inner-test-rewritten/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/inner-test-rewritten/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/inner-test-rewritten/{c}", "templated" → true)
                ),
                "c" → 567
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/test-rewritten/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = mockContext(request)
      val filteredResponse = afterFilters.filterResponse(context, response).futureValue
      val linksMap = filteredResponse.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/v3/test/1"))

      val e = filteredResponse.body.asMap("_embedded")
      val x = e.asMap("x")
      val innerLinksMap = x.__links.fromValue[LinksMap]
      innerLinksMap("self") shouldBe Left(Link(href="/v3/inner-test/9"))

      val y = e.asMap("y")
      y shouldBe LstV(
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/v3/inner-test-rewritten/123")
          ),
          "b" → 123
        ),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/v3/inner-test-rewritten/567")
          ),
          "c" → 567
        )
      )
    }

    "_embedded/_links rewriting and formatting (event)" in {
      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val event = FacadeRequest(Uri("/test-rewritten"), Method.POST, Map.empty,
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/inner-test-rewritten/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/inner-test-rewritten/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/inner-test-rewritten/{c}", "templated" → true)
                ),
                "c" → 567
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/test-rewritten/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = mockContext(request)
      val filteredEvent = afterFilters.filterEvent(context, event).futureValue
      filteredEvent.uri shouldBe Uri("/v3/test")

      val linksMap = filteredEvent.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/v3/test/1"))

      val e = filteredEvent.body.asMap("_embedded")
      val x = e.asMap("x")
      val innerLinksMap = x.__links.fromValue[LinksMap]
      innerLinksMap("self") shouldBe Left(Link(href="/v3/inner-test/9"))

      val y = e.asMap("y")
      y shouldBe LstV(
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/v3/inner-test-rewritten/123")
          ),
          "b" → 123
        ),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/v3/inner-test-rewritten/567")
          ),
          "c" → 567
        )
      )
    }
  }
}
