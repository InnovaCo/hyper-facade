package eu.inn.facade.filter

import eu.inn.facade.filter.chain.Filters
import eu.inn.facade.model._

import scala.concurrent.{Future, ExecutionContext}

class NoOpFilterFactory extends RamlFilterFactory {
  override def createFilters(target: RamlTarget): Filters = {
    Filters(
      requestFilters = Seq.empty,
      responseFilters = Seq(new NoOpFilter),
      eventFilters = Seq.empty
    )
  }
}

class NoOpFilter extends ResponseFilter {
  override def apply(context: ResponseFilterContext, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future.successful(output)
  }
}
