package eu.inn.facade.raml

import eu.inn.hyperbus.transport.api.uri.Uri

case class RewriteIndex(inverted: Map[(Option[Method], String), String], forward: Map[(Option[Method], String), String]) {
  def findOriginal(uri: Uri, requestMethod: String): Uri = {
    var found = false
    var originalUri = uri.pattern.specific
    val method = Method(requestMethod)

    while(!found) {
      findMostSpecificRewriteRule(inverted, method, originalUri) match {
        case Some(foundUri) ⇒
          originalUri = foundUri
        case None ⇒
          found = true
      }
    }
    Uri(originalUri)
  }

  def findNextBack(uri: Uri, requestMethod: String): Uri = {
    findMostSpecificRewriteRule(inverted, Method(requestMethod), uri.pattern.specific) match {
      case Some(foundUri) ⇒
        Uri(foundUri)
      case None ⇒
        uri
    }
  }

  def findFinalDestination(uri: Uri, requestMethod: String): Uri = {
    var found = false
    var rewrittenUri = uri.pattern.specific
    val method = Method(requestMethod)

    while(!found) {
      findMostSpecificRewriteRule(forward, method, rewrittenUri) match {
        case Some(foundUri) ⇒
          rewrittenUri = foundUri
        case None ⇒
          found = true
      }
    }
    Uri(rewrittenUri)
  }

  private def findMostSpecificRewriteRule(index: Map[(Option[Method], String), String], method: Method, originalUri: String): Option[String] = {
    var result: Option[String] = None
    var segmentToRewrite = originalUri
    var slashPosition = segmentToRewrite.length
    while (result.isEmpty && slashPosition != -1) { // exit if we found result or checked all possible URI segments
      segmentToRewrite = segmentToRewrite.substring(0, slashPosition)
      slashPosition = segmentToRewrite.lastIndexOf('/')
      result = findInIndex(index, method, segmentToRewrite)
    }
    result match {
      case Some(rewrittenSegment) ⇒
        val originalSegmentLength = segmentToRewrite.length
        val rewrittenUri = rewrittenSegment + originalUri.substring(originalSegmentLength)
        Some(rewrittenUri)
      case None ⇒
        None
    }
  }

  private def findInIndex(index: Map[(Option[Method], String), String], method: Method, originalUri: String): Option[String] = {
    index.get(Some(method), originalUri) orElse index.get(None, originalUri)
  }
}

object RewriteIndex {
  def apply(): RewriteIndex = {
    new RewriteIndex(Map.empty, Map.empty)
  }
}
