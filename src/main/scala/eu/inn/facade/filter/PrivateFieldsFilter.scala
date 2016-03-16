package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Obj, Value}
import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class PrivateFieldsFilterFactory extends RamlFilterFactory {
  override def createRequestFilter(target: RamlTarget): Option[RequestFilter] = None
  override def createResponseFilter(target: RamlTarget): Option[ResponseFilter] = {
    target match {
      case TargetTypeDeclaration(typeName, fields) ⇒ Some(new PrivateFieldsResponseFilter(fields))
      case _ ⇒ None // log warning
    }
  }
  override def createEventFilter(target: RamlTarget): Option[EventFilter] = {
    target match {
      case TargetTypeDeclaration(typeName, fields) ⇒ Some(new PrivateFieldsEventFilter(fields))
      case _ ⇒ None // log warning
    }
  }
}

trait PrivateFieldsFilter {
  def privateFields: Seq[String]
  def filterBody(body: Value): Value = {
    var bodyFields = body.asMap
    privateFields.foreach { fieldName ⇒
      bodyFields -= fieldName
    }
    Obj(bodyFields)
  }
}

class PrivateFieldsResponseFilter(val privateFields: Seq[String]) extends ResponseFilter with PrivateFieldsFilter {
  override def apply(input: FacadeRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      output.copy(
        body = filterBody(input.body)
      )
    }
  }
}

class PrivateFieldsEventFilter(val privateFields: Seq[String]) extends EventFilter with PrivateFieldsFilter {
  override def apply(input: FacadeRequest, output: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      output.copy(
        body = filterBody(input.body)
      )
    }
  }
}
