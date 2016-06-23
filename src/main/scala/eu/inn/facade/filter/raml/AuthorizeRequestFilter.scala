package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.model.{ContextStorage, ContextWithRequest}
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.authorize
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeRequestFilter(ifExpression: String) extends RequestFilter with MapBasedConditionalFilter {

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {

    Future {
      val ctx = contextWithRequest.context
      val authorized = expressionEngine(contextWithRequest.request, ctx).parse(ifExpression)
      val updatedContextStorage = ctx.contextStorage + (ContextStorage.IS_AUTHORIZED → authorized)
      contextWithRequest.copy (
        context = ctx.copy(
          contextStorage = updatedContextStorage
        )
      )
    }
  }
}

class AuthorizeFilterFactory extends RamlFilterFactory {
  val log = LoggerFactory.getLogger(getClass)

  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {

    target match {

      case TargetResource(_, Annotation(_, Some(auth: authorize))) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter(auth.getIfExpression)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, Annotation(_, Some(auth: authorize))) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter(auth.getIfExpression)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (authorize) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}