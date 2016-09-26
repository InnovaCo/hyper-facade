package eu.inn.facade.filter.model

import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlAnnotation

import scala.concurrent.{ExecutionContext, Future}

case class ConditionalRequestFilterProxy(annotation: RamlAnnotation, filter: RequestFilter, predicateEvaluator: PredicateEvaluator) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest))
          filter.apply(contextWithRequest)
        else
          Future(contextWithRequest)

      case None ⇒
        filter.apply(contextWithRequest)
    }
  }
}

case class ConditionalResponseFilterProxy(annotation: RamlAnnotation, filter: ResponseFilter, predicateEvaluator: PredicateEvaluator) extends ResponseFilter {
  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    annotation.predicate match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest))
          filter.apply(contextWithRequest, response)
        else
          Future(response)

      case None ⇒
        filter.apply(contextWithRequest, response)
    }
  }
}

case class ConditionalEventFilterProxy(annotation: RamlAnnotation, filter: EventFilter, predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest))
          filter.apply(contextWithRequest, event)
        else
          Future(event)

      case None ⇒
        filter.apply(contextWithRequest, event)
    }
  }
}
