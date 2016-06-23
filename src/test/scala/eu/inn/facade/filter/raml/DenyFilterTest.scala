package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Text}
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.{CleanRewriteIndex, FacadeConfigPaths, MockContext}
import eu.inn.hyperbus.model.Method
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.ExecutionContext.Implicits.global

class DenyFilterTest extends FreeSpec with Matchers with ScalaFutures with Injectable with CleanRewriteIndex with MockContext {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "specific-raml-configs/deny-filter-test.raml")
  implicit val injector = Injectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")

  "DenyFilterTest" - {
    "request. private resource. forbidden" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/authorized-only-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(unauthorizedRequest), unauthorizedRequest)
      val fail = ramlFilters.filterRequest(cwr).failed.futureValue
      fail shouldBe a [FilterInterruptException]
    }

    "request. private resource. allowed" in {
      val request = FacadeRequest(
        Uri("/authorized-only-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(request)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.IS_AUTHORIZED → true)
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), request)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest shouldBe cwr
    }

    "request. private method. forbidden" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/authorized-only-method"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(unauthorizedRequest), unauthorizedRequest)
      val fail = ramlFilters.filterRequest(cwr).failed.futureValue
      fail shouldBe a [FilterInterruptException]
    }

    "request. private method. allowed" in {
      val request = FacadeRequest(
        Uri("/authorized-only-method"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(request)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.IS_AUTHORIZED → true)
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), request)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest shouldBe cwr
    }

    "response. private fields. filter fields" in {
      val request = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(request), request)
      val response = FacadeResponse(
        200,
        Map.empty,
        Obj(Map(
            "publicField" → Text("public"),
            "privateField" → Text("secret")
          )
        )
      )
      val filteredResponse = ramlFilters.filterResponse(cwr, response).futureValue
      filteredResponse shouldBe FacadeResponse(200, Map.empty, Obj(Map("publicField" → Text("public"))))
    }

    "response. private fields. don't filter fields" in {
      val request = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(request)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.IS_AUTHORIZED → true)
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), request)

      val response = FacadeResponse(
        200,
        Map.empty,
        Obj(Map(
            "publicField" → Text("public"),
            "privateField" → Text("secret")
          )
        )
      )
      val filteredResponse = ramlFilters.filterResponse(cwr, response).futureValue
      filteredResponse shouldBe response
    }

    "event. private fields. filter fields" in {
      val request = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(request), request)
      val event = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.FEED_PUT,
        Map.empty,
        Map(
          "publicField" → Text("public"),
          "privateField" → Text("secret")
        )
      )
      val expectedEvent = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.FEED_PUT,
        Map.empty,
        Map("publicField" → Text("public"))
      )
      val filteredEvent = ramlFilters.filterEvent(cwr, event).futureValue
      filteredEvent shouldBe expectedEvent
    }

    "event. private fields. don't filter fields" in {
      val request = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(request)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.IS_AUTHORIZED → true)
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), request)
      val event = FacadeRequest(
        Uri("/authorized-only-fields"),
        Method.FEED_PUT,
        Map.empty,
        Map(
          "publicField" → Text("public"),
          "privateField" → Text("secret")
        )
      )
      val filteredEvent = ramlFilters.filterEvent(cwr, event).futureValue
      filteredEvent shouldBe event
    }
  }
}
