package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Null, ObjV}
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
  RewriteIndexHolder.updateRewriteIndex("/test-rewrite", "/rewritten", None)
  RewriteIndexHolder.updateRewriteIndex("/rewritten", "/rewritten-twice", None)

  "RewriteEventFilter" - {
    "rewrite links" in {
      val filter = new RewriteEventFilter

      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val event = FacadeRequest(
        Uri("/rewritten/some-service"), Method.POST, Map.empty, ObjV("field" → "content")
      )

      val requestContext = mockContext(request)

      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(requestContext, event), 10.seconds)
      }

      val expectedEvent = FacadeRequest(
        Uri("/test-rewrite/some-service"), Method.POST, Map.empty,
        ObjV("field" → "content"))

      restartException.facadeRequest shouldBe expectedEvent
    }
  }
}
