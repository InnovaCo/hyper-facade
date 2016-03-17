package eu.inn.facade.filter

import eu.inn.binders.dynamic.Null
import eu.inn.facade.filter.chain.{FilterChainRaml, FilterChain}
import eu.inn.facade.model._
import eu.inn.facade.modules.{ConfigModule, FiltersModule}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.{Injectable, Module}

class FilterChainRamlFactoryTest extends FreeSpec with Matchers with Injectable {


  implicit val injector = new ConfigModule :: new FiltersModule :: new Module {
    bind [Seq[RamlFilterFactory]] identifiedBy "paged" to Seq(new NoOpFilterFactory)
  }
  injector.initNonLazy()
  val filterChain = inject [FilterChain].asInstanceOf[FilterChainRaml]

  "FilterChainRamlFactory " - {
    "trait based filter chain" in {
      val filters = filterChain.requestFilters(FacadeRequest(Uri("/private"), "get", Map.empty, Null))
      filters.length should equal(1)
      filters.head shouldBe a[PrivateResourceFilter]
    }

    "annotation based filter chain" in {
      val filters = filterChain.requestFilters(FacadeRequest(Uri("/status/test-service"), "get", Map.empty, Null))
      filters.length should equal(1)
      filters.head shouldBe a[EnrichRequestFilter]
    }

    "trait and annotation based filter chain" in {
      val filters = filterChain.responseFilters(
        FacadeRequest(Uri("/users"), "get", Map.empty, Null),
        FacadeResponse(200, Map.empty, Null)
      )

      val defaultResponseFilters = inject [Seq[Filter]]("defaultResponseFilters")

      filters.size should equal(defaultResponseFilters.size + 2)
      filters.head shouldBe a[NoOpFilter]
      filters.tail.head shouldBe a[PrivateFieldsFilter]
      filters.tail.tail should equal(defaultResponseFilters)
    }
  }
}
