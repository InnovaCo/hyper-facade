package eu.inn.facade.filter.http

import eu.inn.binders.value._
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.FacadeRequest
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.RewriteIndexHolder
import eu.inn.hyperbus.model.Link
import eu.inn.hyperbus.model.Links._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class HttpWsRequestFilterTest extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll with Injectable with MockContext {

  implicit val injector = Injectors()
  val beforeFilters = inject[FilterChain]("beforeFilterChain")
  override def beforeAll() = {
    RewriteIndexHolder.clearIndex()
    RewriteIndexHolder.updateRewriteIndex("/test/{a}", "/rewritten/{a}", None)
    RewriteIndexHolder.updateRewriteIndex("/test/abc", "/rewritten/abc", None)
    RewriteIndexHolder.updateRewriteIndex("/rewritten/{a}", "/rewritten-twice/{a}", None)
    RewriteIndexHolder.updateRewriteIndex("/rewritten/abc", "/rewritten-twice/abc", None)
    RewriteIndexHolder.updateRewriteIndex("/inner-test/{a}", "/inner-rewritten/{a}", None)
    RewriteIndexHolder.updateRewriteIndex("/inner-test/{b}", "/inner-rewritten/{b}", None)
  }

  "HttpWsRequestFilterTest " - {
    "_links rewriting and formatting" in {
      val request = FacadeRequest(Uri("/v3/test"), "post", Map.empty,
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/v3/test/{a}", "templated" → true),
            "some-other1" → ObjV("href" → "/v3/test/abc", "templated" → false),
            "some-other2" → ObjV("href" → "/v3/test/xyz"),
            "some-other3" → List(
              ObjV("href" → "/v3/test/abc123"),
              ObjV("href" → "/v333/test/abc123"),
              ObjV("href" → "/v3/test/{b}", "templated" → true)
            )
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val context = mockContext(request)
      val filteredRequest = beforeFilters.filterRequest(context, request).futureValue(Timeout(Span(500, Seconds)))
      filteredRequest.uri shouldBe Uri("/test")

      val linksMap = filteredRequest.body.__links.fromValue[LinksMap] // binders deserialization magic
      linksMap("self") shouldBe Left(Link(href="/rewritten-twice/1", templated = false))
      linksMap("some-other1") shouldBe Left(Link(href="/rewritten-twice/abc", templated = false))
      linksMap("some-other2") shouldBe Left(Link(href="/test/xyz", templated = false))  // URI left as is because there is no record in RewriteIndex with formatted URI '/test/xyz'
      linksMap("some-other3") shouldBe Right(
        Seq(
          Link(href="/test/abc123", templated = false),  // URI left as is because there is no record in RewriteIndex with formatted URI '/test/abc123'
          Link(href="/v333/test/abc123", templated = false),  // URI left as is because prefix '/v333' doesn't match configured root prefix '/v3'
          Link(href="/test/2", templated = false))  // URI left as is because there is no record in RewriteIndex with templated URI '/test/{b}'
      )
    }

    "_embedded/_links rewriting and formatting" in {
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
                  "self" → ObjV("href" → "/v3/inner-test/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/v3/inner-test/567", "templated" → false)
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
      linksMap("self") shouldBe Left(Link(href="/v333/test/1", templated = false))

      val e = filteredRequest.body.asMap("_embedded")
      val x = e.asMap("x")
      val innerLinksMap = x.__links.fromValue[LinksMap]
      innerLinksMap("self") shouldBe Left(Link(href="/inner-rewritten/9", templated = false))

      val y = e.asMap("y")
      y shouldBe LstV(
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/inner-rewritten/123")
          ),
          "b" → 123
        ),
        ObjV(
          "_links" → ObjV(
            "self" → ObjV("href" → "/inner-test/567", "templated" → false)
          )
        )
      )
    }
  }
}
