package eu.inn.facade.utils

import eu.inn.facade.raml.RewriteIndex
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}
import spray.http.Uri.Path

object UriTransformer {

  def rewriteToOriginal(method: String, rewriteIndex: Option[RewriteIndex])(from: Uri): Uri = {
    rewriteIndex match {
      case Some(index) ⇒
        val to = index.findOriginal(from, method)
        rewrite(from, to)
      case None ⇒
        from
    }
  }

  def rewriteOneStepBack(method: String, rewriteIndex: Option[RewriteIndex])(from: Uri): Uri = {
    rewriteIndex match {
      case Some(index) ⇒
        val to = index.findNextBack(from, method)
        rewrite(from, to)
      case None ⇒
        from
    }
  }

  def rewriteOneStepForward(from: Uri, toUri: String): Uri = {
    rewrite(from, Uri(toUri))
  }

  def rewriteForward(method: String, rewriteIndex: Option[RewriteIndex])(from: Uri): Uri = {
    rewriteIndex match {
      case Some(index) ⇒
        val to = index.findFinalDestination(from, method)
        rewrite(from, to)
      case None ⇒
        from
    }
  }

  private def rewrite(from: Uri, to: Uri): Uri = {
    val toUriPath = to.pattern.specific
    val toUriParams = UriParser.extractParameters(to.pattern.specific)
    val newArgs = toUriParams flatMap { uriParameter ⇒
      from.args.get(uriParameter) match {
        case Some(matcher) ⇒
          Some(uriParameter → matcher)
        case None ⇒
          throw new IllegalArgumentException(s"No parameter argument specified for $uriParameter on $from")
      }
    }
    Uri(Uri(Specific(toUriPath), newArgs.toMap).formatted)
  }

  def addRootPathPrefix(baseUri: String, rootPathPrefix: String)(uri: Uri): Uri = {
    val normalizedUri = spray.http.Uri(uri.pattern.specific)
    if (normalizedUri.path.startsWith(Path(baseUri))) {
      val prefixOffset = normalizedUri.scheme.length + baseUri.length
      val pathOffset = prefixOffset + rootPathPrefix.length
      val oldPattern = uri.pattern.specific
      val newPattern = oldPattern.substring(0, prefixOffset) + rootPathPrefix + oldPattern.substring(pathOffset)
      Uri(Specific(newPattern), uri.args)
    } else
      uri
  }

  def removeRootPathPrefix(baseUri: String, rootPathPrefix: String)(uri: Uri): Uri = {
    val normalizedUri = spray.http.Uri(uri.pattern.specific)
    if (normalizedUri.path.startsWith(Path(baseUri + rootPathPrefix))) {
      val prefixOffset = normalizedUri.scheme.length + baseUri.length
      val pathOffset = prefixOffset + rootPathPrefix.length
      val oldPattern = uri.pattern.specific
      val newPattern = oldPattern.substring(0, prefixOffset) + oldPattern.substring(pathOffset)
      Uri(Specific(newPattern), uri.args)
    } else
      uri
  }
}

case class RewriteResult(uri: Uri, failures: Seq[Throwable])
