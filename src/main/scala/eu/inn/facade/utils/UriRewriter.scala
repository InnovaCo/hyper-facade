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

  def addRootPathPrefix(uri: Uri, rootPathPrefix: String): Uri = {
    Uri(Specific(rootPathPrefix + uri.pattern.specific), uri.args)
  }

  def removeRootPathPrefix(uri: Uri, rootPathPrefix: String): Uri = {
    val newPattern = uri.pattern.specific.substring(rootPathPrefix.length)
    Uri(Specific(newPattern), uri.args)
  }
}

case class RewriteResult(uri: Uri, failures: Seq[Throwable])
