package eu.inn.facade.filter.raml

import eu.inn.binders.dynamic.{Obj, Value}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import eu.inn.facade.raml.Field

import scala.concurrent.{ExecutionContext, Future}

class PrivateFieldsFilterFactory extends RamlFilterFactory {
  def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetFields(typeName, fields) ⇒
        SimpleFilterChain(
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
  override def apply(context: ResponseFilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.copy(
        body = filterBody(response.body)
      )
    }
  }
}

class PrivateFieldsEventFilter(val privateFields: Seq[Field]) extends EventFilter with PrivateFieldsFilter {
  override def apply(context: EventFilterContext, response: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      response.copy(
        body = filterBody(response.body)
      )
    }
  }
}
