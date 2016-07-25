package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Value}
import eu.inn.facade.filter.model.{EventFilter, PredicateEvaluator, RequestFilter, ResponseFilter}
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.deny
import eu.inn.facade.raml.{Annotation, Field}
import eu.inn.hyperbus.model.{ErrorBody, Forbidden}

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

class DenyResponseFilter(val fields: Seq[Field], predicateEvaluator: PredicateEvaluator) extends ResponseFilter {

  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.copy(
        body = DenyFilter.filterBody(fields, response.body, contextWithRequest, predicateEvaluator)
      )
    }
  }
}

class DenyEventFilter(val fields: Seq[Field], predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      event.copy(
        body = DenyFilter.filterBody(fields, event.body, contextWithRequest, predicateEvaluator)
      )
    }
  }
}

object DenyFilter {
  def filterBody(fields: Seq[Field], body: Value, contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): Value = {
    body match {
      case _: Obj ⇒
        val filteredFields = filterFields(fields, body.asMap, contextWithRequest, predicateEvaluator)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  def filterFields(ramlFields: Seq[Field], fields: scala.collection.Map[String, Value], contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): scala.collection.Map[String, Value] = {
    ramlFields.foldLeft(fields) { (nonPrivateFields, ramlField) ⇒
      if (isPrivateField(ramlField, contextWithRequest, predicateEvaluator))
        nonPrivateFields - ramlField.name
      else {
        if (shouldFilterNestedFields(ramlField, nonPrivateFields)) {
          val fieldName = ramlField.name
          val nestedFields = nonPrivateFields(fieldName).asMap
          val filteredNestedFields = filterFields(ramlField.fields, nestedFields, contextWithRequest, predicateEvaluator)
          nonPrivateFields + (ramlField.name → Obj(filteredNestedFields))
        } else
          nonPrivateFields
      }
    }
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

  def shouldFilterNestedFields(ramlField: Field, fields: scala.collection.Map[String, Value]): Boolean = {
    ramlField.fields.nonEmpty &&
      fields.nonEmpty &&
      fields.contains(ramlField.name)
  }
}
