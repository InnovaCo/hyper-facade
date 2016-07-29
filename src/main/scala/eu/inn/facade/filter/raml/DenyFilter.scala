package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Value}
import eu.inn.facade.filter.model.{EventFilter, PredicateEvaluator, RequestFilter, ResponseFilter}
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.deny
import eu.inn.facade.raml.{Annotation, Field}
import eu.inn.hyperbus.model.{ErrorBody, Forbidden}

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

class DenyRequestFilter extends RequestFilter {

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val error = Forbidden(ErrorBody("forbidden"))
      throw new FilterInterruptException(
        FacadeResponse(error),
        s"Access to resource ${contextWithRequest.request.uri} is forbidden"
      )
    }
  }
}

class DenyResponseFilter(val field: Field, predicateEvaluator: PredicateEvaluator) extends ResponseFilter {

  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.copy(
        body = DenyFilter.filterBody(field, response.body, contextWithRequest, predicateEvaluator)
      )
    }
  }
}

class DenyEventFilter(val field: Field, predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      event.copy(
        body = DenyFilter.filterBody(field, event.body, contextWithRequest, predicateEvaluator)
      )
    }
  }
}

object DenyFilter {
  def filterBody(field: Field, body: Value, contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): Value = {
    body match {
      case _: Obj ⇒
        val filteredFields = filterFields(field, body.asMap, contextWithRequest, predicateEvaluator)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  def filterFields(ramlField: Field, fields: scala.collection.Map[String, Value], contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): scala.collection.Map[String, Value] = {
    if (isPrivateField(ramlField, contextWithRequest, predicateEvaluator))
      erasePrivateField(ramlField.name, fields)
    else
      fields
  }

  def erasePrivateField(pathToField: String, nonPrivateFields: Map[String, Value]): Map[String, Value] = {
    if (pathToField.contains("."))
      pathToField.split(".", 1).toList match {
        case (leadPathSegment :: tailPath :: Nil) ⇒
          nonPrivateFields.get(leadPathSegment) match {
            case Some(subFields) ⇒
              erasePrivateField(tailPath, subFields.asMap)
          }
      }
    else
      nonPrivateFields - pathToField
  }


  def isPrivateField(field: Field, contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): Boolean = {
    field.annotations.find(_.name == Annotation.DENY) match {
      case Some(Annotation(_, Some(deny: deny))) ⇒
        Option(deny.getPredicate) match {
          case Some(predicate) ⇒
            predicateEvaluator.evaluate(predicate, contextWithRequest.request, contextWithRequest.context)
          case None ⇒
            true
        }
      case None ⇒
        false
    }
  }
}
