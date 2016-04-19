package eu.inn.facade.filter.http

import com.typesafe.config.Config
import eu.inn.binders.value._
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.model._
import eu.inn.facade.utils.HalTransformer
import eu.inn.facade.utils.UriTransformer._
import eu.inn.hyperbus.model.Links._
import eu.inn.hyperbus.model.{DefLink, Header}
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.http.HttpHeaders

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config) extends ResponseFilter {
  override def apply(context: FacadeRequestContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = rewriteToOriginal _ andThen addRootPathPrefix(rootPathPrefix) _
      val (newHeaders, newBody) = HttpWsFilter.filterMessage(response, uriTransformer)
      response.copy(
        headers = newHeaders,
        body = newBody
      )
    }
  }
}

class WsEventFilter(config: Config) extends EventFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = rewriteToOriginal _ andThen addRootPathPrefix(rootPathPrefix) _
      val (newHeaders, newBody) = HttpWsFilter.filterMessage(request, uriTransformer)
      request.copy(
        uri = uriTransformer(Uri(request.uri.formatted)),
        headers = newHeaders,
        body = newBody
      )
    }
  }
}

object HttpWsFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap

  def filterMessage(message: FacadeMessage, uriTransformer: (Uri ⇒ Uri)): (Map[String, Seq[String]], Value) = {
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

    val newBody = HalTransformer.transformEmbeddedObject(message.body, uriTransformer)
    if (newBody.isInstanceOf[Obj] /* && response.status == 201*/ ) {
      // Created, set header value
      newBody.__links.fromValue[Option[LinksMap]].flatMap(_.get(DefLink.LOCATION)) match {
        case Some(Left(l)) ⇒
          val newHref = Uri(l.href).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case Some(Right(la)) ⇒
          val newHref = Uri(la.head.href).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case _ ⇒
      }
    }

    (headersBuilder.result(), newBody)
  }
}