package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.{FilterChainFactory, FilterChainRamlFactory}
import eu.inn.facade.filter.model.{OutputFilter, InputFilter, Filter}
import eu.inn.facade.filter._
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

import scala.collection.JavaConversions._

class FiltersModule extends Module {

  bind [Seq[Filter]]        identifiedBy "privateResource"                      to Seq(new PrivateResourceFilter)
  bind [Seq[Filter]]        identifiedBy "privateField"                         to Seq(new PrivateFieldsFilter(inject [RamlConfig] ) with InputFilter,
                                                                                       new PrivateFieldsFilter(inject [RamlConfig] ) with OutputFilter)
  bind [Seq[Filter]]        identifiedBy "x-client-ip" and "x-client-language"  to Seq(new EnrichmentFilter(inject [RamlConfig] ) with InputFilter,
                                                                                       new EnrichmentFilter(inject [RamlConfig] ) with OutputFilter)
  bind [FilterChainFactory] identifiedBy 'ramlFilterChain                       to new FilterChainRamlFactory

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
