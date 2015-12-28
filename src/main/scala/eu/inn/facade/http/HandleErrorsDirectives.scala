package eu.inn.facade.http

import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.http._
import spray.routing._

class HandleErrorsDirectives extends Directives with JsonSupport {

  object ErrorFormatter {
    def apply(f: ErrorFormatter) = f
  }

  type ErrorFormatter = PartialFunction[Any, String]


  val log = LoggerFactory.getLogger(HandleErrorsDirectives.this.getClass.getName)
  protected val ErrorHandlerHeader = "X-Errors-Handled"
  protected val RateLimitRemainingHeader = "X-Rate-Limit-Remaining"

  def DefaultErrorFormatter: ErrorFormatter = {
    case e: ErrorMessage ⇒
      serialize(Map(
        "error"   → dasherize(Option(e.error).getOrElse(StatusCode.int2StatusCode(e.httpStatus).reason)),
        "message" → e.getMessage
      ) ++ e._links.map(x ⇒ "_links" → x).toMap)

    case e: Throwable ⇒
      serialize(Map(
        "error"   → "internal-error",
        "message" → "Internal server error",
        "cause"   → e.getClass.getName
      ))

    case response: HttpResponse ⇒
      if (!response.headers.exists(_.name == ErrorHandlerHeader)) {
        serialize(Map("error" → dasherize(response.status.reason), "message" → response.entity.asString))
      } else {
        response.entity.asString
      }
  }

  def handleErrors(formatter: ErrorFormatter): Directive0 =
    handleExceptions(customExceptionHandler(formatter)) &
      handleRejections(customRejectionHandler(formatter)) &
      mapHttpResponse(wrapHttpResponse(_, formatter)) &
      respondWithSingletonHeader(RawHeader(ErrorHandlerHeader, "1"))

  private def customExceptionHandler(formatter: ErrorFormatter) =
    ExceptionHandler {
      case e: TooManyRequestError ⇒
        respondWithSingletonHeader(RawHeader(RateLimitRemainingHeader, "0")) {
          complete(e.httpStatus, formatter.applyOrElse(e, DefaultErrorFormatter))
        }

      case e: UnauthorizedError ⇒
        respondWithSingletonHeader(HttpHeaders.`WWW-Authenticate`(HttpChallenge("Bearer", "4game", Map("error" → e.getMessage)))) {
          complete(e.httpStatus, formatter.applyOrElse(e, DefaultErrorFormatter))
        }

      case e: ErrorMessage ⇒
        complete(e.httpStatus, formatter.applyOrElse(e, DefaultErrorFormatter))

      case e: akka.pattern.AskTimeoutException ⇒
        log.error(e.getMessage, e)
        complete(StatusCodes.GatewayTimeout, formatter.applyOrElse(new ErrorMessage(504, "Request timed out, please try again later.", error = "timeout"), DefaultErrorFormatter))

      case e: Throwable if e.getCause != null && e.getCause.isInstanceOf[IllegalArgumentException] ⇒
        complete(StatusCodes.BadRequest, formatter.applyOrElse(new BadRequestError(e.getCause.getMessage, e), DefaultErrorFormatter))

      case e @ (_: IllegalArgumentException | _: JsonProcessingException | _: IllegalUriException) ⇒
        complete(StatusCodes.BadRequest, formatter.applyOrElse(new BadRequestError(e.getMessage, e), DefaultErrorFormatter))

      case e: java.io.FileNotFoundException ⇒
        complete(NotFound, formatter.applyOrElse(new NotFoundError(e.getMessage, e), DefaultErrorFormatter))

      case e: java.io.IOException ⇒
        log.error("Service unavailable: " + e, e)
        complete(ServiceUnavailable, formatter.applyOrElse(new ServiceUnavailableError("Service unavailable"), DefaultErrorFormatter))

      case e: Throwable ⇒
        log.error("Internal error: " + e, e)
        complete(StatusCodes.InternalServerError, formatter.applyOrElse(e, DefaultErrorFormatter))
    }

  private def customRejectionHandler(formatter: ErrorFormatter) =
    RejectionHandler { case rejections ⇒
      mapHttpResponse(wrapHttpResponse(_, formatter.orElse(DefaultErrorFormatter)))(RejectionHandler.Default(rejections.filterNot(_.isInstanceOf[MethodRejection])))
    }

  private def wrapHttpResponse[T](response: HttpResponse, formatter: ErrorFormatter): HttpResponse = response match {
    case resp if resp.status.isFailure && formatter.isDefinedAt(resp) ⇒
      response.mapEntity(_.flatMap(_.copy(
        data = HttpData(formatter(response)).asInstanceOf[HttpData.NonEmpty]
      )))

    case _ ⇒ response
  }

  private def dasherize(s: String) = s.replaceAll("\\W+", "-").toLowerCase
}
