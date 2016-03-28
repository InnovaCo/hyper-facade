package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Null, ObjV, Text}
import eu.inn.facade.filter.raml.{EnrichRequestFilter, RewriteRequestFilter}
import eu.inn.facade.model.{FacadeRequest, FilterRestartException, RequestFilterContext}
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.raml.{Annotation, DataType, Field, Method}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RewriteRequestFilterTest extends FreeSpec with Matchers with ScalaFutures {
  "RewriteFilter" - {
    "simple rewrite" in {
      val args = new rewrite()
      args.setUri("/new-resource")
      val filter = new RewriteRequestFilter(args)

      val request = FacadeRequest(
        Uri("/resource"),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      val requestFilterContext = RequestFilterContext(request.uri, request.method, Map.empty, Null)

      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(requestFilterContext, request), 10.seconds)
      }

      val expectedRequest = FacadeRequest(
        Uri("/new-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value")))

      restartException.request shouldBe expectedRequest
    }

    "rewrite with arguments" in {
      val args = new rewrite()
      args.setUri("/users/{userId}/new-resource")
      val filter = new RewriteRequestFilter(args)

      val request = FacadeRequest(
        Uri("/resource/{userId}", Map("userId" → "100500")),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      val requestFilterContext = RequestFilterContext(request.uri, request.method, Map.empty, Null)

      val restartException = intercept[FilterRestartException]{
        Await.result(filter.apply(requestFilterContext, request), 10.seconds)
      }

      val expectedRequest = FacadeRequest(
        Uri("/users/{userId}/new-resource", Map("userId" → "100500")),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      restartException.request shouldBe expectedRequest
    }
  }
}
