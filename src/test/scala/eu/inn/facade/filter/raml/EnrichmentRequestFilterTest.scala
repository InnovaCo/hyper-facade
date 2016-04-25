package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text}
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.FacadeRequest
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.{Annotation, DataType, Field, Method}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class EnrichmentRequestFilterTest extends FreeSpec with Matchers with ScalaFutures with MockContext with Injectable {
  implicit val injector = Injectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")

  "EnrichmentFilter" - {
    "add fields if request headers are present" in {
      val filter = new EnrichRequestFilter(Seq(
          Field("clientIp", DataType.DEFAULT_TYPE_NAME, Seq(Annotation(Annotation.CLIENT_IP)), Seq.empty),
          Field("acceptLanguage", DataType.DEFAULT_TYPE_NAME, Seq(Annotation(Annotation.CLIENT_LANGUAGE)), Seq.empty)))

      val request = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map("Accept-Language" → Seq("ru")),
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
        filteredRequest.copy(headers=Map.empty) shouldBe expectedRequest
      }
    }

    "don't add fields if request headers are missed" in {
      val filter = new EnrichRequestFilter(Seq(
        Field("acceptLanguage", DataType.DEFAULT_TYPE_NAME, Seq(Annotation(Annotation.CLIENT_LANGUAGE)), Seq.empty)))

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

    "nested fields" in {
      val request = FacadeRequest(Uri("/complex-resource"), "post", Map.empty,
        Obj(Map("value" → Obj(
                  Map("publicField" → Text("new value"))
              )
            )
        )
      )
      val context = mockContext(request)
      val enrichedRequest = ramlFilters.filterRequest(context, request).futureValue
      val fields = enrichedRequest.body.asMap
      val valueSubFields = fields("value").asMap
      valueSubFields("address") shouldBe Text("127.0.0.1")
      valueSubFields("publicField") shouldBe Text("new value")
    }
  }
}
