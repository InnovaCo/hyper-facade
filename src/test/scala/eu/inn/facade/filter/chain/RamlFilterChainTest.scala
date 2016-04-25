package eu.inn.facade.filter.chain

import eu.inn.binders.value.{Null, ObjV}
import eu.inn.facade.MockContext
import eu.inn.facade.filter.NoOpFilter
import eu.inn.facade.filter.raml._
import eu.inn.facade.model.{FacadeRequest, _}
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

// todo: important to test when specific != formatted!
// + integrated test with filter lookup when specific != formatted!

class RamlFilterChainTest extends FreeSpec with Matchers with Injectable with MockContext {
  implicit val injector = Injectors()

  val filterChain = inject [FilterChain].asInstanceOf[RamlFilterChain]

  "FilterChainRamlFactory " - {
    "resource filter chain" in {
      val request = FacadeRequest(Uri("/private"), "get", Map.empty, Null)
      val context = mockContext(request)
      val filters = filterChain.findRequestFilters(request)
      filters.length should equal(1)
      filters.head shouldBe a[RequestPrivateFilter]
    }

    "annotation based filter chain" in {
      val request = FacadeRequest(Uri("/status/test-service"), "get", Map.empty, Null)
      val context = mockContext(request)
      val filters = filterChain.findRequestFilters(request)
      filters.length should equal(1)
      filters.head shouldBe a[EnrichRequestFilter]
    }

    "trait and annotation based filter chain" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map.empty, Null)
      val filters = filterChain.findResponseFilters(mockContext(request), response)

      filters.head shouldBe a[NoOpFilter]
      filters.tail.head shouldBe a[ResponsePrivateFilter]
    }

    "response filter chain (annotation fields)" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map.empty, ObjV("statusCode" → 100500, "processedBy" → "John"))
      val filters = filterChain.findResponseFilters(mockContext(request), response)
      filters.head shouldBe a[NoOpFilter]
      filters.tail.head shouldBe a[ResponsePrivateFilter]
      filters.length should equal(2)
    }

    "event filter chain (annotation fields)" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val event = FacadeRequest(request.uri, "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val filters = filterChain.findEventFilters(mockContext(request), event)
      filters.head shouldBe a[EventPrivateFilter]
      filters.length should equal(1)
    }

    "rewrite filters. forward request filters, inverted event filters" in {
      val request = FacadeRequest(Uri("/test-rewrite/some-service"), "get", Map.empty, Null)
      val context = mockContext(request.copy(uri=Uri(request.uri.formatted)))
      val event = FacadeRequest(Uri("/status/test-service"), "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val requestFilters = filterChain.findRequestFilters(request)
      val eventFilters = filterChain.findEventFilters(context, event)

      requestFilters.head shouldBe a[RewriteRequestFilter]
  //      eventFilters.head shouldBe a[RewriteEventFilter] this shouldn't happen!
    }

    "rewrite filters with args. forward request filters, inverted event filters" in {
      val request = FacadeRequest(Uri("/test-rewrite-with-args/some-service/{arg}", Map("arg" → "100500")), "get", Map.empty, Null)
      val event = FacadeRequest(Uri("/status/test-service/100501"), "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val requestFilters = filterChain.findRequestFilters(request)
      val eventFilters = filterChain.findEventFilters(mockContext(request), event)

      requestFilters.head shouldBe a[RewriteRequestFilter]
      eventFilters.head shouldBe a[RewriteEventFilter]
    }

    "rewrite filters. forward request filters, inverted event filters with patterns" in {
      val request = FacadeRequest(Uri("/test-rewrite-method/some-service"), "put", Map.empty, Null)
      val event = FacadeRequest(Uri("/revault/content/{path:*}", Map("path" → "some-service")), "feed:put", Map.empty, Null)
      val notMatchedEvent = FacadeRequest(Uri("/revault/content/{path:*}", Map("path" → "other-service")), "feed:put", Map.empty, Null)

      val requestFilters = filterChain.findRequestFilters(request)
      val eventFilters = filterChain.findEventFilters(mockContext(request), event)
      val notMatchedEventFilters = filterChain.findEventFilters(mockContext(request), notMatchedEvent)

      requestFilters.head shouldBe a[RewriteRequestFilter]
      eventFilters.head shouldBe a[RewriteEventFilter]
      notMatchedEventFilters.head shouldBe a[RewriteEventFilter]  // we assign filter chain on templated URI, not on formatted one, so despite
                                                                  // formatted URI of this event doesn't match rewritten URI from RAML config,
                                                                  // rewrite filter will be assigned to this event, but will do nothing
    }
  }
}