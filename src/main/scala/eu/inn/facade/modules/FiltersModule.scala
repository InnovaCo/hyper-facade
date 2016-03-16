package eu.inn.facade.modules

import eu.inn.facade.ConfigsFactory
import eu.inn.facade.filter._
import eu.inn.facade.filter.chain.{FilterChainFactory, FilterChainRamlFactory}
import eu.inn.facade.model.{RamlFilterFactory, ResponseFilter, Filter}
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

import scala.collection.JavaConversions._

class FiltersModule extends Module {

  bind [Seq[RamlFilterFactory]]   identifiedBy "privateResource"                          to Seq(new PrivateResourceFilterFactory)
  bind [Seq[RamlFilterFactory]]   identifiedBy "x-client-ip" and "x-client-language"      to Seq(new EnrichmentFilterFactory)
  bind [Seq[RamlFilterFactory]]   identifiedBy  "privateField"                            to Seq(new PrivateFieldsFilterFactory)

  bind [Seq[ResponseFilter]]  identifiedBy "defaultResponseFilters"               to Seq(new RevisionHeadersFilter)
  bind [FilterChainFactory] identifiedBy 'ramlFilterChain                       to new FilterChainRamlFactory
  initOuterBindings()

  def initOuterBindings(): Unit = {
    val config = new ConfigsFactory().config
    if (config.hasPath("inn.facade.filters")) {
      val declaredRamlFilters = config.getConfig("inn.facade.filters")
      declaredRamlFilters.entrySet.foreach { filterConfigEntry ⇒
        val filterName = filterConfigEntry.getKey
        val filterClass: String = filterConfigEntry.getValue.unwrapped.asInstanceOf[String]
        val filter = Class.forName(filterClass).newInstance().asInstanceOf[Filter]
        bind[Seq[Filter]] identifiedBy filterName to Seq(filter)
      }
    }
  }
}
