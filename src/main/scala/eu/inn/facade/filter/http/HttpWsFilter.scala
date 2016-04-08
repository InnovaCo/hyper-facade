package eu.inn.facade.filter.http

import com.typesafe.config.Config
import eu.inn.binders.value._
import eu.inn.facade.FacadeConfig
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import eu.inn.facade.utils.{HalTransformer, UriTransformer}
import eu.inn.hyperbus.model.{DefLink, Header}
import eu.inn.hyperbus.model.Links._
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.HttpHeaders

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config, ramlConfig: RamlConfig) extends ResponseFilter {
  override def apply(context: FacadeRequestContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfig.RAML_ROOT_PATH_PREFIX)
      HttpWsFilter.filterMessage(response, rootPathPrefix, ramlConfig.baseUri).asInstanceOf[FacadeResponse]
    }
  }
}

class WsEventFilter(config: Config, ramlConfig: RamlConfig) extends EventFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfig.RAML_ROOT_PATH_PREFIX)
      HttpWsFilter.filterMessage(request, rootPathPrefix, ramlConfig.baseUri).asInstanceOf[FacadeRequest]
    }
  }
}

object HttpWsFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap

  def filterMessage(message: FacadeMessage, rootPathPrefix: String, baseUri: String): FacadeMessage = {
    val headersBuilder = Map.newBuilder[String, Seq[String]]
    message.headers.foreach {
      case (Header.CONTENT_TYPE, value :: tail) ⇒
        headersBuilder += FacadeHeaders.CONTENT_TYPE →
          FacadeHeaders.genericContentTypeToHttp(Some(value)).toSeq

      case (k, v) ⇒
        if (directHyperbusToFacade.contains(k)) {
          headersBuilder += directHyperbusToFacade(k) → v
        }
    }

    val uriTransformer: (Uri ⇒ Uri) = UriTransformer.addRootPathPrefix(baseUri, rootPathPrefix)

    val newBody = HalTransformer.transformAndFormatEmbeddedObject(message.body, uriTransformer)
    if (newBody.isInstanceOf[Obj] /* && response.status == 201*/ ) {
      // Created, set header value
      newBody.__links.fromValue[Option[LinksMap]].flatMap(_.get(DefLink.LOCATION)) match {
        case Some(Left(l)) ⇒
          val newHref = uriTransformer(Uri(l.href)).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case Some(Right(la)) ⇒
          val newHref = uriTransformer(Uri(la.head.href)).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case _ ⇒
      }
    }

    message match {
      case request: FacadeRequest ⇒
        request.copy(
          uri = uriTransformer(Uri(request.uri.formatted)),
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