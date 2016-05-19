package eu.inn.facade.raml

import eu.inn.hyperbus.transport.api.matchers.Specific
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}

import scala.collection.immutable.SortedMap

case class IndexKey(uri: String, method: Option[Method])

case class RewriteIndex(inverted: Map[IndexKey, String], forward: Map[IndexKey, String]) {
  def findRewriteForward(uri: Uri, requestMethod: Option[String]): Option[Uri] = {
    findRewrite(uri, requestMethod, forward)
  }

  def findRewriteBackward(uri: Uri, requestMethod: Option[String]): Option[Uri] = {
    findRewrite(uri, requestMethod, inverted)
  }

  private def findRewrite(uri: Uri, requestMethod: Option[String], index: Map[IndexKey, String]): Option[Uri] = {
    val method = requestMethod.map(m ⇒ Method(m))
    findMostSpecificRewriteRule(index, method, uri)
  }

  private def findMostSpecificRewriteRule(index: Map[IndexKey, String], method: Option[Method], originalUri: Uri): Option[Uri] = {
    exactMatch(index, method, originalUri.formatted) orElse
      exactMatch(index, method, originalUri.pattern.specific) orElse
      patternMatch(index, method, originalUri) match {
      case Some(matchedUri) ⇒
        val newArgs = originalUri.args ++ matchedUri.args
        Some(matchedUri.copy(
          args = newArgs
        ))
      case None ⇒
        None
    }
  }

  private def exactMatch(index: Map[IndexKey, String], method: Option[Method], originalUri: String): Option[Uri] = {
    index.get(IndexKey(originalUri, method)) orElse
      index.get(IndexKey(originalUri, None)) match {
      case Some(uri) ⇒
        Some(Uri(uri))
      case None ⇒ None
    }
  }

  private def patternMatch(index: Map[IndexKey, String], method: Option[Method], originalUri: Uri): Option[Uri] = {
    var found: Option[Uri] = None
    index.foreach {
      case (key, indexUri) ⇒
        if (found.isEmpty) {
          UriMatcher.matchUri(key.uri, Uri(originalUri.formatted)) match {
            case Some(matchedUri) ⇒
              found = Some(Uri(Specific(indexUri), matchedUri.args))
            case None ⇒
          }
        }
    }
    found
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
    RewriteIndex(SortedMap.empty[IndexKey, String], SortedMap.empty[IndexKey, String])
  }
}
