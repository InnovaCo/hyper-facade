package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

class PrivateFilterFactory(config: Config) extends RamlFilterFactory {
  val log = LoggerFactory.getLogger(getClass)
  val privateAddresses = extractPrivateAddresses(config)

  def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {

      case TargetFields(typeName, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new ResponsePrivateFilter(fields, privateAddresses)),
          eventFilters = Seq(new EventPrivateFilter(fields, privateAddresses))
        )

      case TargetResource(_, _) | TargetMethod(_, _, _) ⇒ SimpleFilterChain(
        requestFilters = Seq(new RequestPrivateFilter(privateAddresses)),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )

      case unknownTarget ⇒
        log.warn(s"Empty filter chain for target $unknownTarget will be created")
        FilterChain.empty
    }
  }

  def extractPrivateAddresses(config: Config): PrivateAddresses = {
    val addresses = for (ip ← config.getStringList("private.addresses")) yield ip
    val networks = for (rangeConfig ← config.getConfigList("private.networks")) yield NetworkRange(rangeConfig.getString("from"), rangeConfig.getString("to"))

    PrivateAddresses(addresses, networks)
  }
}
