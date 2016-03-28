package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.{ConfigsFactory, FacadeConfig}
import eu.inn.facade.filter.chain.{FilterChain, RamlFilterChain, SimpleFilterChain}
import eu.inn.facade.filter.http.{HttpWsRequestFilter, HttpWsResponseFilter, WsEventFilter}
import eu.inn.facade.filter.raml._
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

import scala.collection.JavaConversions._


class FiltersModule extends Module {

  bind [Seq[RamlFilterFactory]]   identifiedBy "private"                              to Seq(new PrivateFilterFactory(inject[Config]))
  bind [Seq[RamlFilterFactory]]   identifiedBy "x-client-ip" and "x-client-language"  to Seq(new EnrichmentFilterFactory)
  bind [Seq[RamlFilterFactory]]   identifiedBy  "rewrite"                             to Seq(new RewriteFilterFactory)

  bind [FilterChain]              identifiedBy "beforeFilterChain"                    to new SimpleFilterChain(
    requestFilters            = Seq(new HttpWsRequestFilter(inject[RamlConfig]))
  )
  bind [FilterChain]              identifiedBy "afterFilterChain"                     to new SimpleFilterChain(
    responseFilters           = Seq(new HttpWsResponseFilter),
    eventFilters              = Seq(new WsEventFilter)
  )
  bind [FilterChain]              identifiedBy "ramlFilterChain"                      to new RamlFilterChain(inject[RamlConfig])

  initOuterBindings()

  def initOuterBindings(): Unit = {
    val config = new ConfigsFactory().config
    if (config.hasPath(FacadeConfig.FILTERS)) {
      val declaredRamlFilters = config.getConfig(FacadeConfig.FILTERS)
      declaredRamlFilters.entrySet.foreach { filterConfigEntry ⇒
        val filterName = filterConfigEntry.getKey
        val filterClass: String = filterConfigEntry.getValue.unwrapped.asInstanceOf[String]
        val filter = Class.forName(filterClass).newInstance().asInstanceOf[RamlFilterFactory]
        bind[Seq[RamlFilterFactory]] identifiedBy filterName to Seq(filter)
      }
    }
  }
}
