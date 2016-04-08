package eu.inn.facade.filter.raml

import eu.inn.binders.value.{ObjV, Text}
import eu.inn.facade.MockContext
import eu.inn.facade.model.{FacadeRequest, FilterRestartException}
import eu.inn.facade.raml._
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RewriteRequestFilterTest extends FreeSpec with Matchers with ScalaFutures with MockContext {
  "RewriteFilter" - {
    "simple rewrite" in {
      val args = new rewrite()
      args.setUri("/rewritten/some-service")
      val filter = new RewriteRequestFilter(args)

      val request = FacadeRequest(
        Uri("/test-rewrite/some-service"),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      val requestContext = mockContext(request)

      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(requestContext, request), 10.seconds)
      }

      val expectedRequest = FacadeRequest(
        Uri("/rewritten/some-service"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value")))

      restartException.facadeMessage shouldBe expectedRequest
    }

    "rewrite with arguments" in {
      val args = new rewrite()
      args.setUri("/test-rewrite/some-service/{serviceId}")
      val filter = new RewriteRequestFilter(args)

      val request = FacadeRequest(
        Uri("/rewritten/some-service/{serviceId}", Map("serviceId" → "100500")),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      val requestContext = mockContext(request)
      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(requestContext, request), 10.seconds)
      }

      val expectedRequest = FacadeRequest(
        Uri("/test-rewrite/some-service/100500"),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      restartException.facadeMessage shouldBe expectedRequest
    }
  }
}
