package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.model.{ContextStorage, ContextWithRequest}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeRequestFilter extends RequestFilter {

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val ctx = contextWithRequest.context
      val updatedContextStorage = ctx.contextStorage + (ContextStorage.IS_AUTHORIZED → true)
      contextWithRequest.copy (
        context = ctx.copy(
          contextStorage = updatedContextStorage
        )
      )
    }
  }
}

class AuthorizeFilterFactory(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetResource(_, _) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, _) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (authorize) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}