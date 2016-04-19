package eu.inn.facade.raml

import eu.inn.facade.raml.RewriteIndex.UriTemplateOrdering
import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}

import scala.collection.immutable.SortedMap

case class IndexKey(uri: String, method: Option[Method])

case class RewriteIndex(inverted: Map[IndexKey, String], forward: Map[IndexKey, String]) {
  def findNextForward(uri: Uri, requestMethod: Option[String]): Option[Uri] = {
    findNext(uri, requestMethod, forward)
  }

  def findNextBack(uri: Uri, requestMethod: Option[String]): Option[Uri] = {
    findNext(uri, requestMethod, inverted)
  }

  private def findNext(uri: Uri, requestMethod: Option[String], index: Map[IndexKey, String]): Option[Uri] = {
    val method = requestMethod match {
      case Some(m) ⇒ Some(Method(m))
      case None ⇒ None
    }
    findMostSpecificRewriteRule(index, method, uri)
  }

  private def findMostSpecificRewriteRule(index: Map[IndexKey, String], method: Option[Method], originalUri: Uri): Option[Uri] = {
    var result: Option[Uri] = None
    var segmentToRewrite = originalUri.pattern.specific
    var slashPosition = segmentToRewrite.length
    while (result.isEmpty && slashPosition != -1) { // exit if we found result or checked all possible URI segments
      segmentToRewrite = segmentToRewrite.substring(0, slashPosition)
      slashPosition = segmentToRewrite.lastIndexOf('/')
      result = findInIndex(index, method, segmentToRewrite)
    }
    result match {
      case Some(matchedUri) ⇒
        val matchedUriSegment = matchedUri.pattern.specific
        val originalSegmentLength = segmentToRewrite.length
        val rewrittenUriSegment = Specific(matchedUriSegment + originalUri.pattern.specific.substring(originalSegmentLength))
        Some(Uri(rewrittenUriSegment, matchedUri.args ++ originalUri.args))
      case None ⇒
        None
    }
  }

  private def findInIndex(index: Map[IndexKey, String], method: Option[Method], originalUri: String): Option[Uri] = {
    var foundUri: Option[Uri] = None
    index foreach {
      case (key, valueUri) if key.method.isEmpty || method == key.method  ⇒
        UriMatcher.matchUri(key.uri, Uri(originalUri)) match {
          case Some(uri) ⇒
            if (foundUri.isEmpty)
              foundUri = Some(Uri(valueUri))
            else if (UriTemplateOrdering.compareUriTemplates(valueUri, foundUri.get.pattern.specific) > 0)
              foundUri = Some(Uri(valueUri))

          case None ⇒
        }
      case _ ⇒ // do nothing if request method is specified and doesn't match one rewrite is allowed for
    }
    foundUri
  }
}

object RewriteIndex {
  implicit object UriTemplateOrdering extends Ordering[IndexKey] {
    override def compare(left: IndexKey, right: IndexKey): Int = {
      if (left.method.isDefined) {
        if (right.method.isDefined)
          compareUriTemplates(left.uri, right.uri)
        else
          1
      } else if (right.method.isDefined) {
        -1
      } else {
        compareUriTemplates(left.uri, right.uri)
      }
    }

    def compareUriTemplates(left: String, right: String): Int = {
      val leftTokens = UriParser.tokens(left).length
      val rightTokens = UriParser.tokens(right).length
      val uriLengthDiff = leftTokens - rightTokens
      if (uriLengthDiff != 0)
        uriLengthDiff
      else
        left.compareTo(right)
    }
  }

  def apply(): RewriteIndex = {
    new RewriteIndex(SortedMap.empty[IndexKey, String], SortedMap.empty[IndexKey, String])
  }
}
