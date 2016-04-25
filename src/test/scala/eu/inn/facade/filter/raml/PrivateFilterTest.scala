package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Null, Obj, Text}
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.{FacadeRequest, FacadeResponse}
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class PrivateFilterTest extends FreeSpec with Matchers with ScalaFutures  with Injectable with MockContext {
  implicit val injector = Injectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")

  "PrivateFilterTest" - {
    "response - nested field" in {
      val request = FacadeRequest(Uri("/complex-resource"), "post", Map.empty, Null)
      val response = FacadeResponse(200, Map("messageId" → Seq("messageId"), "correlationId" → Seq("correlationId")),
        Obj(Map("name" → Text("resource name"),
                "value" → Obj(
                  Map("privateField" → Text("secret"),
                      "publicField" → Text("resource state")
                  )
                )
            )
        )
      )

      val context = mockContext(request)
      val filteredResponse = ramlFilters.filterResponse(context, response).futureValue
      val fields = filteredResponse.body.asMap
      val valueSubFields = fields("value").asMap

      fields("name") shouldBe Text("resource name")
      valueSubFields.get("privateField") shouldBe None
      valueSubFields("publicField") shouldBe Text("resource state")
    }
  }
}
