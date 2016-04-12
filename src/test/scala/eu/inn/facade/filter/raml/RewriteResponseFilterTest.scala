package eu.inn.facade.filter.raml

import eu.inn.binders.value._
import eu.inn.facade.MockContext
import eu.inn.facade.model.{FacadeRequest, FacadeResponse}
import eu.inn.facade.raml.{Method, RewriteIndexHolder}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class RewriteResponseFilterTest extends FreeSpec with Matchers with ScalaFutures with MockContext {
  "RewriteResponseFilter" - {
    "rewrite links" in {
      RewriteIndexHolder.updateRewriteIndex("/test-rewrite", "/rewritten", None)
      RewriteIndexHolder.updateRewriteIndex("/rewritten", "/rewritten-twice", None)
      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val filter = new RewriteResponseFilter
      val requestContext = mockContext(request)

      val response = FacadeResponse(200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/rewritten-twice/inner-test/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/rewritten-twice/inner-test-x/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/rewritten-twice/inner-test-y/{c}", "templated" → true)
                ),
                "c" → 567
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/rewritten-twice/test/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val filteredResponse = filter.apply(requestContext, response).futureValue
      val expectedResponse = FacadeResponse(
        200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/test-rewrite/inner-test/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/test-rewrite/inner-test-x/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/test-rewrite/inner-test-y/{c}", "templated" → true)
                ),
                "c" → 567
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/test-rewrite/test/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        ))

      filteredResponse shouldBe expectedResponse
    }
  }
}
