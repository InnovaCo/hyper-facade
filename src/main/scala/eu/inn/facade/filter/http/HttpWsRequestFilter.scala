package eu.inn.facade.filter.http

import eu.inn.binders.dynamic.Null
import eu.inn.facade.model._
import eu.inn.facade.raml.RamlConfig
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.{Header, Method, QueryBody}

import scala.concurrent.{ExecutionContext, Future}

class HttpWsRequestFilter(ramlConfig: RamlConfig) extends RequestFilter {

  override def apply(context: RequestFilterContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val httpUri = spray.http.Uri(request.uri.pattern.specific)
      val newUri = ramlConfig.resourceUri(httpUri.path.toString)

      val headersBuilder = Map.newBuilder[String, Seq[String]]
      var messageIdFound = false

      request.headers.foreach {
        case (FacadeHeaders.CONTENT_TYPE, value :: tail)
          if value.startsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_START)
            && value.endsWith(FacadeHeaders.CERTAIN_CONTENT_TYPE_END) ⇒

          val beginIndex = FacadeHeaders.CERTAIN_CONTENT_TYPE_START.length
          val endIndex = value.length - FacadeHeaders.CERTAIN_CONTENT_TYPE_END.length

          headersBuilder += Header.CONTENT_TYPE → Seq(value.substring(beginIndex, endIndex))

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
          (request.body, other)
      }

      FacadeRequest(newUri, newMethod, headersBuilder.result(), newBody)
    }
  }
}

object HttpWsRequestFilter {
  val directFacadeToHyperbus =  FacadeHeaders.directHeaderMapping.toMap
}
