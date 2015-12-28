package eu.inn.facade.filter.chain

import eu.inn.facade.filter.model.Filter
import eu.inn.facade.raml.RamlConfig
import scaldi.{Injectable, Injector}

class FilterChainRamlFactory(implicit inj: Injector) extends FilterChainFactory with Injectable {

  val ramlConfig = inject[RamlConfig]

  override def inputFilterChain(uri: String, method: String): FilterChain = {
    constructFilterChain(uri, method, filter ⇒ filter.isInputFilter)
  }

  override def outputFilterChain(uri: String, method: String): FilterChain = {
    constructFilterChain(uri, method, filter ⇒ filter.isOutputFilter)
  }

  private def constructFilterChain(uri: String, method: String, predicate: Filter ⇒ Boolean): FilterChain = {
    val filterNames = ramlConfig.traits(uri, method)
    var filters = Seq[Filter]()
    filterNames.foreach { filterName ⇒
      val filter = inject[Filter](filterName)
      if (predicate(filter)) filters = filters :+ filter
    }
    FilterChain(filters)
  }
}
