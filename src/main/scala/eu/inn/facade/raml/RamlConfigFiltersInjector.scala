package eu.inn.facade.raml

import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.raml.RewriteEventFilterFactory
import eu.inn.facade.model.{RamlFilterFactory, RamlTarget, TargetMethod, TargetResource}
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.hyperbus.transport.api.uri.Uri
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector, StringIdentifier}

import scala.util.control.NonFatal

class RamlConfigFiltersInjector(resourcesByUri: Map[String, ResourceConfig])(implicit inj: Injector) extends Injectable {

  val log = LoggerFactory.getLogger(getClass)
  val resourcesWithFilters = Map.newBuilder[String, ResourceConfig]

  def withResourceFilters(): Map[String, ResourceConfig] = {
    resourcesWithFilters ++= resourcesByUri
    resourcesByUri.foreach { uriToConfig ⇒
      val (uri, resourceConfig) = uriToConfig
      resourcesWithFilters += uri → injectResourceFilters(uri, resourceConfig)
    }
    resourcesWithFilters.result()
  }

  def injectResourceFilters(uri: String, resourceConfig: ResourceConfig): ResourceConfig = {
    val resourceFilters = createFilters(uri, None, resourceConfig.annotations)
    val resourceMethodsAcc = Map.newBuilder[Method, RamlResourceMethodConfig]
    resourceConfig.methods.foreach { ramlResourceMethod ⇒
      val (method, resourceMethodConfig) = ramlResourceMethod
      resourceMethodsAcc += method → injectMethodFilters(uri, method, resourceMethodConfig, resourceFilters)
    }
    resourceConfig.copy(
      methods = resourceMethodsAcc.result(),
      filters = resourceFilters
    )
  }

  def injectMethodFilters(uri: String, method: Method, resourceMethodConfig: RamlResourceMethodConfig, resourceFilters: SimpleFilterChain): RamlResourceMethodConfig = {
    val methodFilterChain = resourceFilters ++ createFilters(uri, Some(method.name), resourceMethodConfig.annotations)
    val updatedRequests = injectRequestsFilters(resourceMethodConfig.requests, methodFilterChain)
    val updatedResponses = injectResponsesFilters(resourceMethodConfig.responses, methodFilterChain)
    resourceMethodConfig.copy(
      methodFilters = methodFilterChain,
      requests = updatedRequests,
      responses = updatedResponses
    )
  }

  def injectRequestsFilters(requests: RamlRequests, parentFilters: SimpleFilterChain): RamlRequests = {
    val updatedContentTypesConfig = injectContentTypeConfigFilters(requests.ramlContentTypes, parentFilters)
    requests.copy(
      ramlContentTypes = updatedContentTypesConfig
    )
  }

  def injectResponsesFilters(responseMap: Map[Int, RamlResponses], parentFilters: SimpleFilterChain): Map[Int, RamlResponses] = {
    val updatedResponseMap = Map.newBuilder[Int, RamlResponses]
    responseMap.foreach {
      case (responseCode, responses) ⇒
        val updatedContentTypesConfig = injectContentTypeConfigFilters(responses.ramlContentTypes, parentFilters)
        val updatedResponses = responses.copy(
          ramlContentTypes = updatedContentTypesConfig
        )
        updatedResponseMap += responseCode → updatedResponses
    }
    updatedResponseMap.result()
  }

  def injectContentTypeConfigFilters(contentTypesConfig: Map[Option[ContentType], RamlContentTypeConfig],
                                     parentFilters: SimpleFilterChain): Map[Option[ContentType], RamlContentTypeConfig] = {
    val updatedRequests = Map.newBuilder[Option[ContentType], RamlContentTypeConfig]
    contentTypesConfig.foreach {
      case (contentType, ramlContentTypeConfig) ⇒
        val updatedFilters = ramlContentTypeConfig.filters ++ parentFilters
        val updatedContentTypeConfig = ramlContentTypeConfig.copy(
          filters = updatedFilters
        )
        updatedRequests += contentType → updatedContentTypeConfig
    }
    updatedRequests.result()
  }

  private def createFilters(uri: String, method: Option[String], annotations: Seq[Annotation]): SimpleFilterChain = {
    annotations.foldLeft(FilterChain.empty) { (filterChain, annotation) ⇒
      val target = method match {
        case Some(m) ⇒ TargetMethod(uri, m, annotation)
        case None ⇒ TargetResource(uri, annotation)
      }

      try {
        val ident = StringIdentifier(annotation.name)
        inj.getBinding(List(ident)) match {
          case Some(_) ⇒
            if (annotation.name == Annotation.REWRITE)
              injectInvertedRewriteFilters(target)
            val filterFactory = inject[RamlFilterFactory](annotation.name)
            filterFactory.createFilterChain(target)

          case None ⇒
            log.warn(s"Annotation '${annotation.name}' is not bound")
            filterChain
        }
      }
      catch {
        case NonFatal(e) ⇒
          log.error(s"Can't inject filter for $annotation", e)
          filterChain
      }
    }
  }

  def injectInvertedRewriteFilters(target: RamlTarget): Unit = {
    val rewriteEventFilters = inject[RewriteEventFilterFactory].createFilterChain(target)
    val rewriteAnnotation = target match {
      case TargetMethod(_, _, annotation) ⇒ annotation
      case TargetResource(_, annotation) ⇒ annotation
    }
    val uri = rewriteAnnotation.value.get.asInstanceOf[rewrite].getUri
    resourcesByUri.get(uri) match {
      case Some(resourceConfig) ⇒
        val updatedResourceConfig = resourceConfig.copy(
          filters = rewriteEventFilters
        )
        resourcesWithFilters += uri → updatedResourceConfig
      case None ⇒
        matchByUri(uri, resourcesByUri) match {
          case Some((rewrittenUri, resourceConfig)) ⇒
            val updatedResourceConfig = resourceConfig.copy(
              filters = rewriteEventFilters
            )
            resourcesWithFilters += rewrittenUri → updatedResourceConfig
        }
    }
  }

  def matchByUri(uri: String, resourcesByUri: Map[String, ResourceConfig]): Option[(String, ResourceConfig)] = {
    val uris = resourcesByUri.keySet - uri
    UriMatcher.matchUri(Uri(uri), uris.toList) match {
      case Some(matchedUri) ⇒
        val matchedUriStr = matchedUri.pattern.specific
        Some(matchedUriStr, resourcesByUri(matchedUriStr))
      case None ⇒
        None
    }
  }
}

class InvalidRamlConfigException(message: String) extends Exception(message)
