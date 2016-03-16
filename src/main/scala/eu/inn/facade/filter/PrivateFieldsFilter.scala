package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Obj, Value}
import eu.inn.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class PrivateFieldsFilterFactory extends RamlFilterFactory {
  override def createRequestFilter(target: RamlTarget): Option[RequestFilter] = None
  override def createResponseFilter(target: RamlTarget): Option[ResponseFilter] = {
    target match {
      case TargetTypeDeclaration(typeName, fields) ⇒ Some(new PrivateFieldsFilter(fields))
      case _ ⇒ None // log warning
    }
  }
}

class PrivateFieldsFilter(privateFields: Seq[String]) extends ResponseFilter {
  override def apply(input: FacadeRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      output.copy(
        body = filterBody(input.body)
      )
    }
  }

  override def apply(input: FacadeRequest, output: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      output.copy(
        body = filterBody(input.body)
      )
    }
  }

  def filterBody(body: Value): Value = {
    var bodyFields = body.asMap
    privateFields.foreach { fieldName ⇒
      bodyFields -= fieldName
    }
    Obj(bodyFields)
  }
}
