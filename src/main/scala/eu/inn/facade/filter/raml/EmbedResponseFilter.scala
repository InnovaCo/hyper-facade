package eu.inn.facade.filter.raml

import akka.pattern.AskTimeoutException
import eu.inn.binders.value._
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.filter.model._
import eu.inn.facade.filter.parser.PredicateEvaluator
import eu.inn.facade.model.{ContextWithRequest, FacadeRequest, FacadeResponse}
import eu.inn.facade.raml.{EmbedAnnotation, Method}
import eu.inn.facade.utils.FutureUtils
import eu.inn.hyperbus.model.{DynamicBody, ErrorBody, HyperbusException, Response}
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}
import eu.inn.hyperbus.{Hyperbus, IdGenerator, model}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EmbedResponseFilter(relName: String)(implicit inj: Injector) extends ResponseFilter with Injectable {
  private val hyperbus = inject[Hyperbus]

  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    val links = EmbedFilter.extractLinks(relName, response.body)
    FutureUtils.chain(response.body, links.map(uri ⇒ EmbedFilter.embed(uri, _: Value, contextWithRequest, hyperbus))) map { updatedBody ⇒
      response.copy(
        body = updatedBody
      )
    }
  }
}

class EmbedEventFilter(relName: String)(implicit inj: Injector) extends EventFilter with Injectable {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val links = EmbedFilter.extractLinks(relName, event.body)
    val hyperbus = inject[Hyperbus]
    FutureUtils.chain(event.body, links.map(uri ⇒ EmbedFilter.embed(uri, _: Value, contextWithRequest, hyperbus))) map { updatedBody ⇒
      event.copy(
        body = updatedBody
      )
    }
  }
}

object EmbedFilter {
  private val log = LoggerFactory.getLogger(getClass)
  def extractLinks(relName: String, body: Value): Seq[Uri] = {
    body match {
      case Obj(fields) ⇒
        fields.get("_links") match {
          case Some(Obj(links)) ⇒
            links.get(relName) match {
              case Some(links: Lst) ⇒ // json+hal when link is array
                links.v.foldLeft(Seq.newBuilder[Uri]) { (uris, linkValue) ⇒
                  if (linkValue.href.isDefined) {
                    uris += formattedUri(linkValue, body)
                  } else {
                    uris
                  }
                }.result()
              case Some(linkValue: Value) ⇒ // json+hal - single link
                Seq(formattedUri(linkValue, body))
            }
          case _ ⇒ Seq.empty
        }
      case _ ⇒ Seq.empty
    }
  }

  private def formattedUri(linkValue: Value, body: Value): Uri = {
    val href = linkValue.href.asString
    if (linkValue.templated.fromValue[Option[Boolean]].contains(true)) { // templated link, have to format
    val tokens = UriParser.extractParameters(href)
      val args = tokens.map { arg ⇒
        arg → body.asMap(arg).asString             // todo: support inner fields + handle exception if not exists?
      }.toMap
      Uri(href, args)
    } else {
      Uri(href)
    }
  }

  def embed(uri: Uri, body: Value, cwr: ContextWithRequest, hyperbus: Hyperbus)(implicit ec: ExecutionContext): Future[Value] = {
    val request = FacadeRequest(uri, Method.GET, cwr.request.headers, Null).toDynamicRequest
    hyperbus <~ request recover {
      handleHyperbusExceptions(cwr, uri)
    } map( response ⇒ response.body.content + body)
  }

  private def handleHyperbusExceptions(cwr: ContextWithRequest, uri: Uri) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusException: HyperbusException[ErrorBody] ⇒
      hyperbusException

    case _: NoTransportRouteException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      model.NotFound(ErrorBody("not-found", Some(s"'$uri' is not found.")))

    case _: AskTimeoutException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling ${cwr.context}")
      model.GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${cwr.context.pathAndQuery}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, cwr)
  }

  private def handleInternalError(exception: Throwable, cwr: ContextWithRequest): Response[ErrorBody] = {
    implicit val mcf = cwr.context.clientMessagingContext()
    val errorId = IdGenerator.create()
    log.error(s"Exception #$errorId while handling ${cwr.context}", exception)
    model.InternalServerError(ErrorBody("internal-server-error", Some(exception.getClass.getName + ": " + exception.getMessage), errorId = errorId))
  }
}

class EmbedFilterFactory (implicit inj: Injector) extends RamlFilterFactory with Injectable {
  private val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetResource(_, EmbedAnnotation(_, _, relName)) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new EmbedResponseFilter(relName)),
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, EmbedAnnotation(_, _, relName)) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new EmbedResponseFilter(relName)),
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (embed) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}
