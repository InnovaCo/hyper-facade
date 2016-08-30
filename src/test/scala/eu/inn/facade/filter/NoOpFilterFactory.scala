package eu.inn.facade.filter

import eu.inn.facade.filter.chain.SimpleFilterChain
import eu.inn.facade.filter.model._
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class NoOpFilterFactory extends RamlFilterFactory {
  val predicateEvaluator = PredicateEvaluator()

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters = Seq.empty,
      responseFilters = Seq(new NoOpFilter(target)),
      eventFilters = Seq.empty
    )
  }
}

class NoOpFilter(target: RamlTarget) extends ResponseFilter {
  override def apply(contextWithRequest: ContextWithRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future.successful(output)
  }
  override def toString = s"NoOpFilter@${this.hashCode}/$target"
}
