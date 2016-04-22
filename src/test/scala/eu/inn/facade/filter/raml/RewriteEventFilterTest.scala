package eu.inn.facade.filter.raml

import eu.inn.binders.value.Null
import eu.inn.facade.MockContext
import eu.inn.facade.model.{FacadeRequest, FilterRestartException}
import eu.inn.facade.raml.{Method, RewriteIndexHolder}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RewriteEventFilterTest extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll with MockContext {

  override def beforeAll() = {
    RewriteIndexHolder.clearIndex()
    RewriteIndexHolder.updateRewriteIndex("/test-rewrite/{service}", "/rewritten/{service}", None)
  }

  "RewriteEventFilter" - {
    "rewrite links" in {
      val filter = new RewriteEventFilter

      val request = FacadeRequest(Uri("/test"), Method.GET, Map.empty, Null)
      val event = FacadeRequest(
        Uri("/rewritten/{service}", Map("service" → "some-service")), Method.POST, Map.empty, Null
      )

      val context = mockContext(request.copy(uri=Uri(request.uri.formatted))).prepareNext(request)

      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(context, event), 10.seconds)
      }

      val expectedEvent = FacadeRequest(
        Uri("/test-rewrite/some-service"), Method.POST, Map.empty,Null
      )

      restartException.facadeRequest shouldBe expectedEvent
    }
  }
}
