package eu.inn.facade.filter

import eu.inn.binders.value.{Obj, Text}
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.FacadeRequest
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class EnrichmentRequestFilterTest extends FreeSpec with Matchers with ScalaFutures  with Injectable with MockContext {
  implicit val injector = Injectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")

  "EnrichmentRequestFilterTest" - {
    "nested fields" in {
      val request = FacadeRequest(Uri("/complex-resource"), "post", Map.empty,
        Obj(Map("value" → Obj(
                  Map("publicField" → Text("new value"))
              )
            )
        )
      )
      val context = mockContext(request).prepare(request)

      val enrichedRequest = ramlFilters.filterRequest(context, request).futureValue
      val fields = enrichedRequest.body.asMap
      val valueSubFields = fields("value").asMap
      valueSubFields("address") shouldBe Text("127.0.0.1")
      valueSubFields("publicField") shouldBe Text("new value")
    }
  }
}
