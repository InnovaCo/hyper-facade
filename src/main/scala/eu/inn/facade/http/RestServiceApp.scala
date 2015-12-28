package eu.inn.facade.http

import java.util.regex.Pattern

import akka.actor.ActorSystem
import akka.event.Logging._
import akka.util.Timeout
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}
import spray.http._
import spray.routing._
import spray.routing.directives.LogEntry

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class RestServiceApp(interface: String, port: Int)(implicit inj: Injector) extends SimpleRoutingApp
  with Injectable {

  implicit val timeout = Timeout(10 seconds)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  private val conf = inject[Config].getConfig("inn.util.http.rest-service")
  private val handleErrorsDirectives = inject[HandleErrorsDirectives]
  private val ErrorHandlerHeader = "X-Errors-Handled"
  val log = LoggerFactory.getLogger(RestServiceApp.this.getClass.getName)

  def start(initRoutes: ⇒ Route) {
    startServer(interface, port) {
      startWithDirectives(initRoutes)
    } onComplete {
      case Success(_) ⇒
        log.info("HttpService successfully started.")

      case Failure(e) ⇒
        log.error(s"Error on bind server to $interface:$port", e)
        sys.exit(1)
    }
  }

  def startWithDirectives(initRoutes: ⇒ Route): Route = {
    enableAccessLogIf(conf.getBoolean("access-log.enabled")) {
      addJsonMediaTypeIfNotExists {
        respondWithCORSHeaders(conf.getStringList("cors.allowed-origins"), conf.getStringList("cors.allowed-paths").map(Pattern.compile)) {
          mapHttpResponseHeaders(_.filterNot(_.name == ErrorHandlerHeader)) {
            handleErrorsDirectives.handleErrors(handleErrorsDirectives.DefaultErrorFormatter) {
              pathSuffix(Slash.?) {
                initRoutes
              }
            }
          }
        }
      }
    }
  }

  private def respondWithCORSHeaders(allowedOrigins: Seq[String], allowedPaths: Seq[Pattern] = Nil): Directive0 =
    optionalHeaderValueByName("Origin") flatMap {
      case Some(origin) ⇒
        if (allowedOrigins.isEmpty || allowedOrigins.exists(origin.endsWith)) {
          requestInstance flatMap { request ⇒
            (if (request.method == HttpMethods.OPTIONS) {
              mapHttpResponse(resp ⇒ resp.copy(status = StatusCodes.OK, entity = HttpEntity.Empty, headers = resp.headers.filterNot(_.is("www-authenticate"))))
            } else Directive.Empty) &
              mapHttpResponseHeaders(headers ⇒
                headers ::: List(
                  HttpHeaders.`Access-Control-Allow-Origin`(SomeOrigins(Seq(origin))),
                  HttpHeaders.`Access-Control-Expose-Headers`("Content-Length", headers.map(_.name): _*),
                  HttpHeaders.RawHeader("Access-Control-Allow-Methods", (Seq(request.method) ++ request.headers.find(_.lowercaseName == "access-control-request-method").map(_.value)).mkString(", ")),
                  HttpHeaders.RawHeader("Access-Control-Allow-Headers", request.headers.find(_.lowercaseName == "access-control-request-headers").map(_.value).getOrElse("Accept")),
                  HttpHeaders.`Access-Control-Allow-Credentials`(allow = true),
                  HttpHeaders.`Access-Control-Max-Age`(86400)
                )
              )
          }
        } else if (allowedPaths.nonEmpty) {
          requestUri flatMap { uri ⇒
            if (allowedPaths.exists(_.matcher(uri.path.toString()).matches())) {
              respondWithCORSHeaders(Nil)
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
        } else {
          reject(AuthorizationFailedRejection)
        }

      case _ ⇒ noop
    }

  private def addJsonMediaTypeIfNotExists: Directive0 =
    mapHttpResponseEntity(_.flatMap {
      case ent if ent.contentType == ContentTypes.`text/plain(UTF-8)` ⇒
        ent.copy(contentType = ContentTypes.`application/json`)

      case ent ⇒ ent
    })

  private def enableAccessLogIf(enabled: Boolean): Directive0 =
    if (enabled) logRequestResponse(accessLogger _)
    else noop

  private def accessLogger(request: HttpRequest): HttpResponsePart ⇒ Option[LogEntry] = {
    val startTime = System.currentTimeMillis

    {
      case resp: HttpResponse ⇒
        Some(LogEntry(
          s"${request.method} ${request.uri} ${request.entity.asString} <--- ${resp.status} "
            + (if (resp.entity.toOption.exists(_.contentType.mediaType.isImage)) resp.entity.toOption.map(_.contentType) else resp.entity.asString.take(1000))
            + s" ${System.currentTimeMillis - startTime} ms",
          if (resp.status.isSuccess || resp.status.intValue == 404) InfoLevel else WarningLevel
        ))

      case _ ⇒ None
    }
  }
}

trait RestRoutes {

  implicit val defaultTimeout = Timeout(10 seconds)

  def routes: Route
}