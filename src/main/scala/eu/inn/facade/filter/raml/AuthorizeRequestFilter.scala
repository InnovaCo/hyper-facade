package eu.inn.facade.filter.raml

import eu.inn.facade.filter.model.{MapBasedConditionalFilter, RequestFilter}
import eu.inn.facade.model.{ContextStorage, ContextWithRequest}

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeRequestFilter(val ifExpression: String) extends RequestFilter with MapBasedConditionalFilter {

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