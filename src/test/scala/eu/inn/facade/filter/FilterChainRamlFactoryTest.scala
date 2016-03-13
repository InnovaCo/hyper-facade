package eu.inn.facade.filter

import eu.inn.facade.filter.chain.FilterChainFactory
import eu.inn.facade.filter.model.Filter
import eu.inn.facade.modules.{ConfigModule, FiltersModule}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.{Injectable, Module}

class FilterChainRamlFactoryTest extends FreeSpec with Matchers with Injectable {


  implicit val injector = new ConfigModule :: new FiltersModule :: new Module {
    bind [Seq[Filter]] identifiedBy "paged" to Seq(new NoOpFilter)
  }
  injector.initNonLazy()
  val filterChainFactory = inject [FilterChainFactory]

  "FilterChainRamlFactory " - {
    "trait based filter chain" in {
      val chain = filterChainFactory.inputFilterChain(Uri("/private"), "get", None)

      chain.filters shouldBe inject [Seq[Filter]]("privateResource")
    }

    "annotation based filter chain" in {
      val chain = filterChainFactory.inputFilterChain(Uri("/status/test-service"), "get", None)

      val inputEnrichmentFilter = inject [Seq[Filter]]("x-client-ip").filter(_.isInputFilter)
      chain.filters shouldBe inputEnrichmentFilter
    }

    "trait and annotation based filter chain" in {
      val chain = filterChainFactory.outputFilterChain(Uri("/users"), "get")

      val outputPrivateFieldFilter = inject [Seq[Filter]]("privateField").filter(_.isOutputFilter)
      val pagedOutputFilter = inject [Seq[Filter]]("paged")
      chain.filters shouldBe pagedOutputFilter ++ outputPrivateFieldFilter
    }
  }
}
