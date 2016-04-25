package eu.inn.facade.raml

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
    val method = requestMethod match {
      case Some(m) ⇒ Some(Method(m))
      case None ⇒ None
    }
    findMostSpecificRewriteRule(index, method, uri)
  }

  private def findMostSpecificRewriteRule(index: Map[IndexKey, String], method: Option[Method], originalUri: Uri): Option[Uri] = {
    findInIndex(index, method, originalUri.formatted) orElse
      findInIndex(index, method, originalUri.pattern.specific) match {
      case Some(matchedUri) ⇒
        Some(matchedUri.copy(
          args = originalUri.args
        ))
      case None ⇒
        None
    }
  }

  private def findInIndex(index: Map[IndexKey, String], method: Option[Method], originalUri: String): Option[Uri] = {
    index.get(IndexKey(originalUri, method)) orElse
      index.get(IndexKey(originalUri, None)) match {
      case Some(uri) ⇒
        Some(Uri(uri))
      case None ⇒ None
    }
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
