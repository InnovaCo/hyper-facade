package eu.inn.facade.filter.http

import com.typesafe.config.Config
import eu.inn.binders.value._
import eu.inn.facade.FacadeConfig
import eu.inn.facade.model._
import eu.inn.facade.utils.{HalTransformer, UriRewriter}
import eu.inn.hyperbus.model.Links._
import eu.inn.hyperbus.model.{DefLink, Header}
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.HttpHeaders

import scala.concurrent.ExecutionContext

class OutputFilter(config: Config) {
  def filterMessage(context: FacadeRequestContext, message: FacadeMessage)
                   (implicit ec: ExecutionContext): FacadeMessage = {
    val headersBuilder = Map.newBuilder[String, Seq[String]]
    message.headers.foreach {
      case (Header.CONTENT_TYPE, value :: tail) ⇒
        headersBuilder += FacadeHeaders.CONTENT_TYPE →
          FacadeHeaders.genericContentTypeToHttp(Some(value)).toSeq

      case (k, v) ⇒
        if (OutputFilter.directHyperbusToFacade.contains(k)) {
          headersBuilder += OutputFilter.directHyperbusToFacade(k) → v
        }
    }

    val newBody = HalTransformer.transformEmbeddedObject(message.body)
    if (newBody.isInstanceOf[Obj] /* && response.status == 201*/ ) {
      // Created, set header value
      newBody.__links.fromValue[Option[LinksMap]].flatMap(_.get(DefLink.LOCATION)) match {
        case Some(Left(l)) ⇒
          headersBuilder += (HttpHeaders.Location.name → Seq(l.href))
        case Some(Right(la)) ⇒
          headersBuilder += (HttpHeaders.Location.name → Seq(la.head.href))
        case _ ⇒
      }
    }

    message match {
      case request: FacadeRequest ⇒
        val rootPathPrefix = config.getString(FacadeConfig.RAML_ROOT_PATH)
        request.copy(
          uri = UriRewriter.addRootPathPrefix(Uri(request.uri.formatted), rootPathPrefix),
          headers = headersBuilder.result(),
          body = newBody
        )

      case response: FacadeResponse ⇒
        response.copy(
          headers = headersBuilder.result(),
          body = newBody
        )
    }
  }
}

object OutputFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}
