package eu.inn.facade.filter.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import eu.inn.facade.raml.Field

import scala.concurrent.{ExecutionContext, Future}

class EnrichmentFilterFactory extends RamlFilterFactory {
  def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetFields(typeName, fields) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new EnrichRequestFilter(fields)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )
      case _ ⇒ FilterChain.empty // log warning
    }
  }
}

class EnrichRequestFilter(val privateFields: Seq[Field]) extends RequestFilter {

  /*
  def enrichBody(headers: TransitionalHeaders, body: DynamicBody): DynamicBody = {
    var enrichedBody = body
    val dataStructure = getDataStructure(headers)
    dataStructure match {
      case Some(structure) ⇒
        structure.body match {
          case Some(httpBody) ⇒
            val fields = httpBody.dataType.fields
            fields.filter(_.dataType.annotations.exists(_.name == CLIENT_LANGUAGE))
              .foreach { field ⇒
                val clientLanguage = headers.headerOption("Accept-Language")
                enrichedBody = clientLanguage match {
                  case Some(language) ⇒
                    val bodyFields = body.content.asMap + ((field.name, Text(language)))
                    DynamicBody(Obj(bodyFields))

                  case None ⇒ body
                }
              }
            fields.filter(_.dataType.annotations.exists(_.name == CLIENT_IP))
              .foreach { field ⇒
                val clientIp = headers.headerOption("http_x_forwarded_for")
                enrichedBody = clientIp match {
                  case Some(ip) ⇒
                    val bodyFields = body.content.asMap + ((field.name, Text(ip)))
                    DynamicBody(Obj(bodyFields))

                  case None ⇒ body
                }
              }
          case None ⇒
        }
      case None ⇒
    }

    enrichedBody
  }
*/

  override def apply(context: RequestFilterContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      request
    }
  }
}
