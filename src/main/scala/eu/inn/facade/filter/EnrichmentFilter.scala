package eu.inn.facade.filter

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.facade.model._
import eu.inn.facade.raml.Annotation._
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.model.DynamicBody

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EnrichmentFilterFactory extends RamlFilterFactory {
  override def createRequestFilter(target: RamlTarget): Option[RequestFilter] = {
    target match {
      case TargetTypeDeclaration(typeName, fields) ⇒ Some(new EnrichRequestFilter(fields))
      case _ ⇒ None // log warning
    }
  }
  override def createResponseFilter(target: RamlTarget): Option[ResponseFilter] = None
  override def createEventFilter(target: RamlTarget): Option[EventFilter] = None
}

class EnrichRequestFilter(val privateFields: Seq[String]) extends RequestFilter {

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

  override def apply(input: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      input
    }
  }
}
