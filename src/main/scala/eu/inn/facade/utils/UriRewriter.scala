package eu.inn.facade.utils

import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.collection.mutable

object UriRewriter {

  def rewrite(from: Uri, toUri: String, toUriParams: Seq[String]): RewriteResult = {
    val failures = mutable.ArrayBuffer[Throwable]()
    val newArgs = toUriParams flatMap { uriParameter ⇒
      from.args.get(uriParameter) match {
        case Some(matcher) ⇒
          Some(uriParameter → matcher)
        case None ⇒
          failures.append(new IllegalArgumentException(s"No parameter argument specified for $uriParameter on $from"))
          None
      }
    }
    val newUri = Uri(Uri(Specific(toUri), newArgs.toMap).formatted)
    RewriteResult(newUri, failures)
  }
}

case class RewriteResult(uri: Uri, failures: Seq[Throwable])
