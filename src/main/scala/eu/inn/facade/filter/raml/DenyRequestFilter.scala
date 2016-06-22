package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Obj, Value}
import eu.inn.facade.filter.model.{EventFilter, MapBasedConditionalFilter, RequestFilter, ResponseFilter}
import eu.inn.facade.model._
import eu.inn.facade.raml.annotationtypes.deny
import eu.inn.facade.raml.{Annotation, Field}
import eu.inn.hyperbus.model.{ErrorBody, Forbidden}
import eu.inn.parser.ExpressionEngine

import scala.concurrent.{ExecutionContext, Future}

class DenyRequestFilter(ifExpression: String) extends RequestFilter with MapBasedConditionalFilter {

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      if (expressionEngine(contextWithRequest.request, contextWithRequest.context).parse(ifExpression))
        contextWithRequest
      else {
        val error = Forbidden(ErrorBody("forbidden"))
        throw new FilterInterruptException(
          FacadeResponse(error),
          s"Access to resource ${contextWithRequest.request.uri} is forbidden"
        )
      }
    }
  }
}

class DenyResponseFilter(val fields: Seq[Field]) extends ResponseFilter with MapBasedConditionalFilter {

  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val exprEngine = expressionEngine(contextWithRequest.request, contextWithRequest.context)
      response.copy(
        body = DenyFilter.filterBody(fields, response.body, contextWithRequest, exprEngine)
      )
    }
  }
}

class DenyEventFilter(val fields: Seq[Field]) extends EventFilter with MapBasedConditionalFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val exprEngine = expressionEngine(contextWithRequest.request, contextWithRequest.context)
      event.copy(
        body = DenyFilter.filterBody(fields, event.body, contextWithRequest, exprEngine)
      )
    }
  }
}

object DenyFilter {
  def filterBody(fields: Seq[Field], body: Value, contextWithRequest: ContextWithRequest, expressionEngine: ExpressionEngine): Value = {
    body match {
      case obj: Obj ⇒
        val filteredFields = filterFields(fields, body.asMap, contextWithRequest, expressionEngine)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  def filterFields(ramlFields: Seq[Field], fields: scala.collection.Map[String, Value], contextWithRequest: ContextWithRequest, expressionEngine: ExpressionEngine): scala.collection.Map[String, Value] = {
    ramlFields.foldLeft(fields) { (nonPrivateFields, ramlField) ⇒
      if (isPrivateField(ramlField, contextWithRequest, expressionEngine))
        nonPrivateFields - ramlField.name
      else {
        if (shouldFilterNestedFields(ramlField, nonPrivateFields)) {
          val fieldName = ramlField.name
          val nestedFields = nonPrivateFields(fieldName).asMap
          val filteredNestedFields = filterFields(ramlField.fields, nestedFields, contextWithRequest, expressionEngine)
          nonPrivateFields + (ramlField.name → Obj(filteredNestedFields))
        } else
          nonPrivateFields
      }
    }
  }

  def isPrivateField(field: Field, contextWithRequest: ContextWithRequest, expressionEngine: ExpressionEngine): Boolean = {
    field.annotations.find(_.name == Annotation.DENY) match {
      case Some(Annotation(_, Some(deny: deny))) ⇒
        expressionEngine.parse(deny.getIfExpression)
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
