package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Obj, Value}
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model._
import eu.inn.facade.raml.Field

import scala.concurrent.{ExecutionContext, Future}

class PrivateFieldsFilterFactory extends RamlFilterFactory {
  def createFilterChain(target: RamlTarget): FilterChain = {
    target match {
      case TargetFields(typeName, fields) ⇒
        FilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new PrivateFieldsResponseFilter(fields)),
          eventFilters = Seq(new PrivateFieldsEventFilter(fields))
        )
      case _ ⇒ FilterChain.empty // log warning
    }
  }
}

trait PrivateFieldsFilter {
  def privateFields: Seq[Field]
  def filterBody(body: Value): Value = {
    var bodyFields = body.asMap
    privateFields.foreach { field ⇒
      bodyFields -= field.name
    }
    Obj(bodyFields)
  }
}

class PrivateFieldsResponseFilter(val privateFields: Seq[Field]) extends ResponseFilter with PrivateFieldsFilter {
  override def apply(input: FacadeRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      output.copy(
        body = filterBody(input.body)
      )
    }
  }
}

class PrivateFieldsEventFilter(val privateFields: Seq[Field]) extends EventFilter with PrivateFieldsFilter {
  override def apply(input: FacadeRequest, output: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      output.copy(
        body = filterBody(input.body)
      )
    }
  }
}
