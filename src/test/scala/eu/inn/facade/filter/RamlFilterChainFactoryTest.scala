package eu.inn.facade.filter

import eu.inn.binders.dynamic.Null
import eu.inn.facade.filter.chain.{RamlFilterChain, FilterChain}
import eu.inn.facade.filter.raml.{EnrichRequestFilter, PrivateResourceFilter, PrivateFieldsFilter}
import eu.inn.facade.model._
import eu.inn.facade.modules.{ConfigModule, FiltersModule}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.{Injectable, Module}

class RamlFilterChainFactoryTest extends FreeSpec with Matchers with Injectable {


  implicit val injector = new ConfigModule :: new FiltersModule :: new Module {
    bind [Seq[RamlFilterFactory]] identifiedBy "paged" to Seq(new NoOpFilterFactory)
  }
  injector.initNonLazy()
  val filterChain = inject [FilterChain].asInstanceOf[RamlFilterChain]

  "FilterChainRamlFactory " - {
    "trait based filter chain" in {
      val request = FacadeRequest(Uri("/private"), "get", Map.empty, Null)
      val context = filterChain.requestFilterContext(request)
      val filters = filterChain.requestFilters(context, request)
      filters.length should equal(1)
      filters.head shouldBe a[PrivateResourceFilter]
    }

    "annotation based filter chain" in {
      val request = FacadeRequest(Uri("/status/test-service"), "get", Map.empty, Null)
      val context = filterChain.requestFilterContext(request)
      val filters = filterChain.requestFilters(context, request)
      filters.length should equal(1)
      filters.head shouldBe a[EnrichRequestFilter]
    }

    "trait and annotation based filter chain" in {
      val request = FacadeRequest(Uri("/users"), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map.empty, Null)
      val context = filterChain.responseFilterContext(request, response)
      val filters = filterChain.responseFilters(context, response)

      filters.head shouldBe a[NoOpFilter]
      filters.tail.head shouldBe a[PrivateFieldsFilter]
    }
  }
}
