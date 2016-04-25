package eu.inn.facade.filter.raml

import eu.inn.binders.value.Null
import eu.inn.facade.MockContext
import eu.inn.facade.model.FacadeRequest
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Method, RewriteIndexHolder}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class RewriteEventFilterTest extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll with MockContext {

  override def beforeAll() = {
    RewriteIndexHolder.clearIndex()
    RewriteIndexHolder.updateRewriteIndex("/test-rewrite/{service}", "/rewritten/{service}", None)
  }

  "RewriteEventFilter" - {
    "rewrite links" in {
      val filter = new RewriteEventFilter(r("/rewritten/{service}"), 10)

      val request = FacadeRequest(Uri("/test-rewrite/{service}", Map("service" → "some-service")), Method.GET, Map.empty, Null)
      val event = FacadeRequest(
        Uri("/rewritten/{service}", Map("service" → "some-service")), Method.POST, Map.empty, Null
      )

      val context = mockContext(request)

      val filteredEvent = filter.apply(context, event).futureValue

      val expectedEvent = FacadeRequest(
        Uri("/test-rewrite/{service}", Map("service" → "some-service")), Method.POST, Map.empty,Null
      )

      filteredEvent shouldBe expectedEvent
    }
  }

  def r(uri: String): rewrite = {
    val res = new rewrite
    res.setUri(uri)
    res
  }
}
