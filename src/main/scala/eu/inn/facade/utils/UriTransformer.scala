package eu.inn.facade.utils

import java.net.MalformedURLException

import eu.inn.facade.raml.{Annotation, RamlConfiguration, RewriteIndexHolder}
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}
import spray.http.Uri.Path


object UriTransformer {
  def rewriteLinkToOriginal(from: Uri, maxRewrites: Int): Uri = {
    if (spray.http.Uri(from.pattern.specific).scheme.nonEmpty)
      from
    else {
      var rewritesLeft = maxRewrites
      var rewrittenUri = from
      while (rewritesLeft > 0) {
        rewritesLeft -= 1
        RewriteIndexHolder.rewriteIndex.findRewriteBackward(rewrittenUri, None) match {
          case Some(uri) ⇒
            rewrittenUri = rewrite(rewrittenUri, uri)
          case None ⇒
            rewritesLeft = 0
        }
      }
      Uri(rewrittenUri.formatted)
    }
  }

  def rewriteBackward(from: Uri, method: String): Uri = {
    if (spray.http.Uri(from.pattern.specific).scheme.nonEmpty)
      from
    else {
      var rewrittenUri = from
      RewriteIndexHolder.rewriteIndex.findRewriteBackward(from, Some(method)) match {
        case Some(uri) ⇒
          rewrittenUri = rewrite(rewrittenUri, uri)
        case None ⇒
      }
      Uri(rewrittenUri.formatted)
    }
  }

  def rewriteLinkForward(from: Uri, maxRewrites: Int, ramlConfig: RamlConfiguration): Uri = {
    if (!linkIsRewriteable(from, ramlConfig))
      from
    else {
      var rewritesLeft = maxRewrites
      var rewrittenUri = from
      while (rewritesLeft > 0) {
        rewritesLeft -= 1
        RewriteIndexHolder.rewriteIndex.findRewriteForward(rewrittenUri, None) match {
          case Some(uri) ⇒
            rewrittenUri = rewrite(rewrittenUri, uri)
          case None ⇒
            rewritesLeft = 0
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
    if (normalizedUri.scheme.isEmpty) {
      if (normalizedUri.path.startsWith(Path(rootPathPrefix + "/"))) {
        val pathOffset = rootPathPrefix.length
        val oldPattern = uri.pattern.specific
        val newPattern = oldPattern.substring(pathOffset)
        Uri(Specific(newPattern), uri.args)
      } else {
        throw new MalformedURLException(s"URI $uri doesn't contain prefix $rootPathPrefix")
      }
    } else {
      throw new MalformedURLException(s"URI $uri contains scheme!")
    }
  }

  def rewrite(from: Uri, to: Uri): Uri = {
    val toUriPath = to.pattern.specific
    val toUriParams = UriParser.extractParameters(to.pattern.specific)
    val newArgs = toUriParams flatMap { uriParameter ⇒
      to.args.get(uriParameter) match {
        case Some(matcher) ⇒
          Some(uriParameter → matcher)
        case None ⇒
          from.args.get(uriParameter) match {
            case Some(matcher) ⇒
              Some(uriParameter → matcher)

            case None ⇒
              throw new IllegalArgumentException(s"No parameter argument specified for '$uriParameter' on $from")
          }
      }
    }
    Uri(Specific(toUriPath), newArgs.toMap)
  }

  private def linkIsRewriteable(from: Uri, ramlConfig: RamlConfiguration): Boolean = {
    val emptyScheme = spray.http.Uri(from.pattern.specific).scheme.isEmpty
    val rewriteAllowed = ramlConfig.resourcesByUri.get(from.pattern.specific) orElse
      ramlConfig.resourcesByUri.get(from.formatted) match {
      case Some(resourceConfig) ⇒
        resourceConfig.annotations.exists(ann ⇒ ann.name == Annotation.REWRITE)
      case None ⇒
        true
    }
    emptyScheme && rewriteAllowed
  }
}
