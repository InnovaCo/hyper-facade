package eu.inn.facade.utils

import eu.inn.facade.raml.RewriteIndexHolder
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}
import spray.http.Uri.Path

object UriTransformer {

  def rewriteToOriginal(from: Uri): Uri = {
    if (spray.http.Uri(from.pattern.specific).scheme.nonEmpty)
      from
    else {
      var found = false
      var rewrittenUri = from
      while (!found) {
        RewriteIndexHolder.rewriteIndex.findNextBack(rewrittenUri, None) match {
          case Some(uri) ⇒
            rewrittenUri = rewrite(rewrittenUri, uri)
          case None ⇒
            found = true
        }
      }
      rewrittenUri
    }
  }

  def rewriteOneStepBack(method: String)(from: Uri): Uri = {
    RewriteIndexHolder.rewriteIndex.findNextBack(from, Some(method)) match {
      case Some(foundUri) ⇒
        rewrite(from, foundUri)
      case None ⇒
        from
    }
  }

  def rewriteOneStepForward(from: Uri, toUri: String): Uri = {
    rewrite(from, Uri(toUri))
  }

  def rewriteForward(from: Uri): Uri = {
    if (spray.http.Uri(from.pattern.specific).scheme.nonEmpty)
      from
    else {
      var found = false
      var rewrittenUri = from
      while (!found) {
        RewriteIndexHolder.rewriteIndex.findNextForward(rewrittenUri, None) match {
          case Some(uri) ⇒
            rewrittenUri = rewrite(rewrittenUri, uri)
          case None ⇒
            found = true
        }
      }
      rewrittenUri
    }
  }

  def addRootPathPrefix(rootPathPrefix: String)(uri: Uri): Uri = {
    val normalizedUri = spray.http.Uri(uri.pattern.specific)
    if (normalizedUri.scheme.isEmpty) {
      val newPattern = rootPathPrefix + uri.pattern.specific
      Uri(Specific(newPattern), uri.args)
    } else
      uri
  }

  def removeRootPathPrefix(rootPathPrefix: String)(uri: Uri): Uri = {
    val normalizedUri = spray.http.Uri(uri.pattern.specific)
    if (normalizedUri.scheme.isEmpty && normalizedUri.path.startsWith(Path(rootPathPrefix + "/"))) {
      val pathOffset = rootPathPrefix.length
      val oldPattern = uri.pattern.specific
      val newPattern = oldPattern.substring(pathOffset)
      Uri(Specific(newPattern), uri.args)
    } else
      uri
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
}
