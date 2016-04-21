package eu.inn.facade.filter.http

import com.typesafe.config.Config
import eu.inn.binders.value.Null
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.model._
import eu.inn.facade.utils.FunctionUtils.chain
import eu.inn.facade.utils.HalTransformer
import eu.inn.facade.utils.UriTransformer._
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.{Header, Method, QueryBody}
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class HttpWsRequestFilter(config: Config) extends RequestFilter {

  override def apply(context: FacadeRequestContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = chain(removeRootPathPrefix(rootPathPrefix), rewriteForward)
      val httpUri = spray.http.Uri(request.uri.pattern.specific)
      val requestUri = removeRootPathPrefix(rootPathPrefix)(Uri(Specific(httpUri.path.toString)))

      val headersBuilder = Map.newBuilder[String, Seq[String]]
      var messageIdFound = false

      request.headers.foreach {
        case (FacadeHeaders.CONTENT_TYPE, value :: tail) ⇒
          headersBuilder += Header.CONTENT_TYPE →  FacadeHeaders.httpContentTypeToGeneric(Some(value)).toSeq

        case (FacadeHeaders.CLIENT_MESSAGE_ID, value :: tail) if value.nonEmpty ⇒
          headersBuilder += Header.MESSAGE_ID → Seq(value)
          messageIdFound = true

        case (k, v) ⇒
          if (HttpWsRequestFilter.directFacadeToHyperbus.contains(k)) {
            headersBuilder += HttpWsRequestFilter.directFacadeToHyperbus(k) → v
          }
      }

      if (!messageIdFound) {
        headersBuilder += Header.MESSAGE_ID → Seq(IdGenerator.create())
      }

      val (newBody, newMethod) = request.method.toLowerCase match {
        case Method.GET | ClientSpecificMethod.SUBSCRIBE ⇒
          (QueryBody.fromQueryString(httpUri.query.toMap).content, Method.GET)
        case Method.DELETE ⇒
          (Null, Method.DELETE)
        case other ⇒
          val transformedBody = HalTransformer.transformEmbeddedObject(request.body, uriTransformer)
          (transformedBody, other)
      }

      FacadeRequest(requestUri, newMethod, headersBuilder.result(), newBody)
    }
  }
}

object HttpWsRequestFilter {
  val directFacadeToHyperbus =  FacadeHeaders.directHeaderMapping.toMap
}
