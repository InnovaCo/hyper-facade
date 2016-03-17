package eu.inn.facade.modules

import eu.inn.facade.ConfigsFactory
import eu.inn.facade.filter.chain.{SimpleFilterChain, RamlFilterChain, FilterChain}
import eu.inn.facade.filter.http.{HttpWsRequestFilter, HttpWsResponseFilter, WsEventFilter}
import eu.inn.facade.filter.raml.{EnrichmentFilterFactory, PrivateFieldsFilterFactory, PrivateResourceFilterFactory}
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

import scala.collection.JavaConversions._

class FiltersModule extends Module {

  bind [Seq[RamlFilterFactory]]   identifiedBy "privateResource"                            to Seq(new PrivateResourceFilterFactory)
  bind [Seq[RamlFilterFactory]]   identifiedBy "x-client-ip" and "x-client-language"        to Seq(new EnrichmentFilterFactory)
  bind [Seq[RamlFilterFactory]]   identifiedBy  "privateField"                              to Seq(new PrivateFieldsFilterFactory)


  bind [FilterChain]              identifiedBy "beforeFilterChain"                          to new SimpleFilterChain(
    initRequestFilters = Seq(new HttpWsRequestFilter)
  )
  bind [FilterChain]              identifiedBy "afterFilterChain"                          to new SimpleFilterChain(
    initResponseFilters = Seq(new HttpWsResponseFilter),
    initEventFilters = Seq(new WsEventFilter)
  )
  bind [FilterChain]              identifiedBy "ramlFilterChain"                            to new RamlFilterChain(inject[RamlConfig])
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
