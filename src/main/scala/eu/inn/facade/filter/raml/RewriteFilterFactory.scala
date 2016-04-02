package eu.inn.facade.filter.raml

import eu.inn.facade.filter.FilterContext
import eu.inn.facade.filter.chain.{FilterChain, SimpleFilterChain}
import eu.inn.facade.model._
import eu.inn.facade.raml.Annotation
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class RewriteFilterFactory extends RamlFilterFactory {
  override def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val rewriteArgs = target match {
      case TargetResource(_, Annotation(_, Some(rewriteArgs : rewrite))) ⇒ Some(rewriteArgs)
      case TargetMethod(_, _, Annotation(_, Some(rewriteArgs : rewrite))) ⇒ Some(rewriteArgs)
      case _ ⇒ None
    }
    rewriteArgs map { args ⇒
      SimpleFilterChain(
        requestFilters = Seq(new RewriteRequestFilter(args)),
        responseFilters = Seq.empty,
        eventFilters = Seq.empty
      )
    } getOrElse {
      FilterChain.empty
    }
  }
}

class RewriteRequestFilter(args: rewrite) extends RequestFilter {
  val uriParameters = UriParser.extractParameters(args.getUri)

  override def apply(context: FilterContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    var failures = mutable.ArrayBuffer[Throwable]()
    val newArgs = uriParameters flatMap { uriParameter ⇒
      request.uri.args.get(uriParameter) match {
        case Some(matcher) ⇒
          Some(uriParameter → matcher)
        case None ⇒
          failures += new IllegalArgumentException(s"No parameter argument specified for $uriParameter on ${request.uri}")
          None
      }
    }

    if (failures.isEmpty) {
      val newUri = Uri(Uri(Specific(args.getUri), newArgs.toMap).formatted)
      val rewrittenRequest = request.copy(
        uri = newUri
      )
      Future.failed(new FilterRestartException(rewrittenRequest, "rewrite"))
    } else {
      Future.failed(failures.head)
    }
  }
}