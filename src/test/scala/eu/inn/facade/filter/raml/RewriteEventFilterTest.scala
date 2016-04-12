package eu.inn.facade.filter.raml

import eu.inn.binders.value.{LstV, Null, ObjV}
import eu.inn.facade.MockContext
import eu.inn.facade.model.{FacadeRequest, FilterRestartException}
import eu.inn.facade.raml.{Method, RewriteIndexHolder}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RewriteEventFilterTest extends FreeSpec with Matchers with ScalaFutures with MockContext {
  "RewriteEventFilter" - {
    "rewrite links" in {
      RewriteIndexHolder.updateRewriteIndex("/test-rewrite", "/rewritten", None)
      RewriteIndexHolder.updateRewriteIndex("/rewritten", "/rewritten-twice", None)
      val filter = new RewriteEventFilter

      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val event = FacadeRequest(
        Uri("/test-rewrite/some-service"), Method.POST, Map.empty,
        ObjV(
          "_embedded" → ObjV(
            "x" → ObjV(
              "_links" → ObjV(
                "self" → ObjV("href" → "/rewritten/inner-test/{a}", "templated" → true)
              ),
              "a" → 9
            ),
            "y" → LstV(
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/rewritten/inner-test-x/{b}", "templated" → true)
                ),
                "b" → 123
              ),
              ObjV(
                "_links" → ObjV(
                  "self" → ObjV("href" → "/rewritten/inner-test-y/{c}", "templated" → true)
                ),
                "c" → 567
              )
            )
          ),
          "_links" → ObjV(
            "self" → ObjV("href" → "/rewritten/test/{a}", "templated" → true)
          ),
          "a" → 1,
          "b" → 2
        )
      )

      val requestContext = mockContext(request)

      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(requestContext, event), 10.seconds)
      }

      val expectedEvent = FacadeRequest(
        Uri("/test-rewrite/some-service"), Method.POST, Map.empty,
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

      restartException.facadeRequest shouldBe expectedEvent
    }
  }
}
