package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.{FilterChainFactory, FilterChainRamlFactory}
import eu.inn.facade.filter.model.Filter
import eu.inn.facade.filter.{InputEnrichmentFilter, OutputEnrichmentFilter, PrivateResourceFilter}
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

import scala.collection.JavaConversions._

class FiltersModule extends Module {

  bind [Filter]             identifiedBy 'private           to new PrivateResourceFilter
  bind [Filter]             identifiedBy 'inputEnrichment   to new InputEnrichmentFilter(inject [RamlConfig] )
  bind [Filter]             identifiedBy 'outputEnrichment  to new OutputEnrichmentFilter(inject [RamlConfig] )
  bind [FilterChainFactory] identifiedBy 'ramlFilterChain   to new FilterChainRamlFactory

  def initOuterBindings: Unit = {
    val config = inject[Config]
    if (config.hasPath("inn.facade.raml.filters")) {
      val declaredRamlFilters = config.getConfig("inn.facade.raml.filters")
      declaredRamlFilters.entrySet.foreach { filterConfigEntry ⇒
        val filterName = filterConfigEntry.getKey
        val filterClass: String = filterConfigEntry.getValue.unwrapped.asInstanceOf[String]
        bind[Filter] identifiedBy filterName to Class.forName(filterClass).asInstanceOf[Filter]
      }
    }
  }
}
