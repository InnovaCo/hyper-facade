package eu.inn.facade.filter.raml

import eu.inn.binders.value.Text
import eu.inn.facade.MockContext
import eu.inn.facade.model.{FacadeHeaders, FacadeRequest}
import eu.inn.facade.raml.{Annotation, DataType, Field, Method}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class EnrichmentRequestFilterTest extends FreeSpec with Matchers with ScalaFutures with MockContext {

  "EnrichmentFilter" - {
    "add fields if request headers are present" in {
      val filter = new EnrichRequestFilter(Seq(
          Field("clientIp", DataType("string", Seq.empty, Seq(Annotation(Annotation.CLIENT_IP)))),
          Field("acceptLanguage", DataType("string", Seq.empty, Seq(Annotation(Annotation.CLIENT_LANGUAGE))))))

      val request = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map("Accept-Language" → Seq("ru"), FacadeHeaders.CLIENT_IP → Seq("127.0.0.1")),
        Map("field" → Text("value"))
      )

      val requestContext = mockContext(request)

      whenReady(filter.apply(requestContext, request), Timeout(Span(10, Seconds))) { filteredRequest ⇒
        val expectedRequest = FacadeRequest(
          Uri("/resource"),
          Method.POST,
          Map.empty,
          Map("field" → Text("value"),
            "clientIp" → Text("127.0.0.1"),
            "acceptLanguage" → Text("ru")))
        filteredRequest shouldBe expectedRequest
      }
    }

    "don't add fields if request headers are missed" in {
      val filter = new EnrichRequestFilter(Seq(
        Field("clientIp", DataType("string", Seq.empty, Seq(Annotation(Annotation.CLIENT_IP)))),
        Field("acceptLanguage", DataType("string", Seq.empty, Seq(Annotation(Annotation.CLIENT_LANGUAGE))))))

      val initialRequest = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map.empty,
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(initialRequest)

      whenReady(filter.apply(requestContext, initialRequest), Timeout(Span(10, Seconds))) { filteredRequest ⇒
        filteredRequest shouldBe initialRequest
      }
    }
  }
}
