package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.deny
import org.slf4j.LoggerFactory

class DenyFilterFactory extends RamlFilterFactory {
  val log = LoggerFactory.getLogger(getClass)

  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {

      case TargetFields(_, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new DenyResponseFilter(fields)),
          eventFilters = Seq(new DenyEventFilter(fields))
        )

      case TargetResource(_, Annotation(_, Some(deny: deny))) ⇒ SimpleFilterChain(
        requestFilters = Seq(new DenyRequestFilter(deny.getIfExpression)),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )

      case TargetMethod(_, _, Annotation(_, Some(deny: deny))) ⇒ SimpleFilterChain(
        requestFilters = Seq(new DenyRequestFilter(deny.getIfExpression)),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )

      case unknownTarget ⇒
        log.warn(s"Annotation (deny) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}
