package eu.inn.facade.filter.raml

import eu.inn.binders.value.Null
import eu.inn.facade.model.{ContextWithRequest, FacadeRequest}
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Method, RewriteIndexHolder}
import eu.inn.facade.{CleanRewriteIndex, MockContext}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class RewriteEventFilterTest extends FreeSpec with Matchers with ScalaFutures with CleanRewriteIndex with MockContext {

  override def beforeAll() = {
    RewriteIndexHolder.updateRewriteIndex("/test-rewrite/{service}", "/rewritten/{service}", None)
  }

  "RewriteEventFilter" - {
    "rewrite links" in {
      val filter = new RewriteEventFilter

      val request = FacadeRequest(Uri("/test-rewrite/{service}", Map("service" → "some-service")), Method.GET, Map.empty, Null)
      val event = FacadeRequest(
        Uri("/rewritten/{service}", Map("service" → "some-service")), Method.POST, Map.empty, Null
      )

      val cwr = ContextWithRequest(mockContext(request), request)
      val filteredEvent = filter.apply(cwr, event).futureValue

      val expectedEvent = FacadeRequest(
        Uri("/test-rewrite/some-service"), Method.POST, Map.empty,Null
      )

      filteredEvent shouldBe expectedEvent
    }
  }
}
