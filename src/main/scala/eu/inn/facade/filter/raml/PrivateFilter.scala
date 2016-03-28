package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.binders.dynamic.{Obj, Value}
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model.{FacadeResponse, _}
import eu.inn.facade.raml.Field
import eu.inn.hyperbus.model.{ErrorBody, NotFound}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class PrivateFilter(val privateFields: Seq[Field]) extends RequestFilter with ResponseFilter with EventFilter {

  override def apply(context: ResponseFilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      response.copy(
        body = filterBody(response.body)
      )
    }
  }

  override def apply(context: RequestFilterContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val error = NotFound(ErrorBody("not_found")) // todo: + messagingContext!!!
    Future.failed(
      new FilterInterruptException(
        FacadeResponse(error),
        message = s"Access to ${request.uri}/${request.method} is restricted"
      )
    )
  }

  override def apply(context: EventFilterContext, response: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      response.copy(
        body = filterBody(response.body)
      )
    }
  }

  private def filterBody(body: Value): Value = {
    var bodyFields = body.asMap
    privateFields.foreach { field ⇒
      bodyFields -= field.name
    }
    Obj(bodyFields)
  }
}

class PrivateFilterFactory extends RamlFilterFactory {
  val log = LoggerFactory.getLogger(getClass)
  def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {

      case TargetFields(typeName, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new PrivateFilter(fields)),
          eventFilters = Seq(new PrivateFilter(fields))
        )

      case TargetResource(_, _) | TargetMethod(_, _, _) ⇒ SimpleFilterChain(
        requestFilters = Seq(new PrivateFilter(Seq.empty)),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )

      case unknownTarget ⇒
        log.warn(s"Empty filter chain for target $unknownTarget will be created")
        FilterChain.empty
    }
  }
}
