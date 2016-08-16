package eu.inn.facade.filter.http

import java.net.MalformedURLException

import com.typesafe.config.Config
import eu.inn.binders.value.Null
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.filter.model.RequestFilter
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import eu.inn.facade.utils.FunctionUtils.chain
import eu.inn.facade.utils.HalTransformer
import eu.inn.facade.utils.UriTransformer._
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class HttpWsRequestFilter(config: Config, ramlConfig: RamlConfig) extends RequestFilter {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      try {
        val request = contextWithRequest.request
        val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
        val uriTransformer = chain(removeRootPathPrefix(rootPathPrefix), rewriteLinkForward(_: Uri, rewriteCountLimit, ramlConfig))
        val httpUri = spray.http.Uri(request.uri.pattern.specific)
        val requestUri = removeRootPathPrefix(rootPathPrefix)(Uri(Specific(httpUri.path.toString)))

        val headersBuilder = Map.newBuilder[String, Seq[String]]
        var messageIdFound = false

        request.headers.foreach {
          case (FacadeHeaders.CONTENT_TYPE, value :: _) ⇒
            headersBuilder += Header.CONTENT_TYPE → FacadeHeaders.httpContentTypeToGeneric(Some(value)).toSeq

          case (FacadeHeaders.CLIENT_MESSAGE_ID, value :: _) if value.nonEmpty ⇒
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

        contextWithRequest.copy(
          request = FacadeRequest(requestUri, newMethod, headersBuilder.result(), newBody)
        )
      } catch {
        case e: MalformedURLException ⇒
          val error = NotFound(ErrorBody("not-found")) // todo: + messagingContext!!!
          throw new FilterInterruptException(
            FacadeResponse(error),
            message = e.getMessage
          )
      }
    }
  }
}

object HttpWsRequestFilter {
  val directFacadeToHyperbus =  FacadeHeaders.directHeaderMapping.toMap
}
