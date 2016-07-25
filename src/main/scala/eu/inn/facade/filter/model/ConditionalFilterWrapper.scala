package eu.inn.facade.filter.model

import eu.inn.facade.filter.model.ConditionalFilterWrapper._
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlAnnotation
import eu.inn.facade.raml.annotationtypes.conditionalAnnotation

import scala.concurrent.{ExecutionContext, Future}

case class ConditionalRequestFilterWrapper(annotation: Option[RamlAnnotation], filter: RequestFilter, predicateEvaluator: PredicateEvaluator) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    predicate(annotation) match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest.request, contextWithRequest.context))
          filter.apply(contextWithRequest)
        else
          Future(contextWithRequest)

      case None ⇒
        filter.apply(contextWithRequest)
    }
  }
}

case class ConditionalResponseFilterWrapper(annotation: Option[RamlAnnotation], filter: ResponseFilter, predicateEvaluator: PredicateEvaluator) extends ResponseFilter {
  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    predicate(annotation) match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest.request, contextWithRequest.context))
          filter.apply(contextWithRequest, response)
        else
          Future(response)

      case None ⇒
        filter.apply(contextWithRequest, response)
    }
  }
}

case class ConditionalEventFilterWrapper(annotation: Option[RamlAnnotation], filter: EventFilter, predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    predicate(annotation) match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest.request, contextWithRequest.context))
          filter.apply(contextWithRequest, event)
        else
          Future(event)

      case None ⇒
        filter.apply(contextWithRequest, event)
    }
  }
}

object ConditionalFilterWrapper {
  def predicate(annotation: Option[RamlAnnotation]): Option[String] = {
    annotation match {
      case Some(ramlAnnotation) ⇒
        ramlAnnotation match {
          case conditionalAnnotation: conditionalAnnotation ⇒
            Option(conditionalAnnotation.getPredicate)

          case _ ⇒
            None
        }

      case None ⇒
        None
    }
  }
}
