package eu.inn.facade.raml

import eu.inn.hyperbus.transport.api.uri.Uri

import scala.collection.mutable

case class RewriteIndex(inverted: Map[(Option[Method], String), String], forward: Map[(Option[Method], String), String]) {
  def findOriginal(uri: Uri, requestMethod: String): Uri = {
    var found = false
    var originalUri = uri.pattern.specific
    val method = Method(requestMethod)

    while(!found) {
      findInIndex(inverted, method, originalUri) match {
        case Some(foundUri) ⇒
          originalUri = foundUri
        case None ⇒
          found = false
      }
    }
    Uri(originalUri)
  }

  def findNextBack(uri: Uri, requestMethod: String): Uri = {
    findInIndex(inverted, Method(requestMethod), uri.pattern.specific) match {
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
      findInIndex(forward, method, rewrittenUri) match {
        case Some(foundUri) ⇒
          rewrittenUri = foundUri
        case None ⇒
          found = false
      }
    }
    Uri(rewrittenUri)
  }

  private def findInIndex(index: Map[(Option[Method], String), String], method: Method, originalUri: String): Option[String] = {
    index.get(Some(method), originalUri) match {
      case foundUri @ Some(_) ⇒
        foundUri
      case None ⇒
        index.get(None, originalUri) match {
          case foundUri @ Some(_) ⇒
            foundUri
          case None ⇒
            None
        }
    }
  }
}

class RewriteIndexBuilder(val invertedIndexBuilder: mutable.Builder[((Option[Method], String), String), Map[(Option[Method], String), String]],
                          val forwardIndexBuilder: mutable.Builder[((Option[Method], String), String), Map[(Option[Method], String), String]]) {
  def append(other: RewriteIndexBuilder): RewriteIndexBuilder = {
    invertedIndexBuilder ++= other.invertedIndexBuilder.result()
    forwardIndexBuilder ++= other.forwardIndexBuilder.result()
    this
  }

  def addInverted(entry: ((Option[Method], String), String)): RewriteIndexBuilder = {
    invertedIndexBuilder += entry
    this
  }

  def addForward(entry: ((Option[Method], String), String)): RewriteIndexBuilder = {
    forwardIndexBuilder += entry
    this
  }

  def build(): RewriteIndex = {
    new RewriteIndex(invertedIndexBuilder.result(), forwardIndexBuilder.result())
  }
}
object RewriteIndexBuilder {
  def apply(): RewriteIndexBuilder = {
    new RewriteIndexBuilder(Map.newBuilder[(Option[Method], String), String], Map.newBuilder[(Option[Method], String), String])
  }
}
