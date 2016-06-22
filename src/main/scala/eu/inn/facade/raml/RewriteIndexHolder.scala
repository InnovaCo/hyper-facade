package eu.inn.facade.raml

object RewriteIndexHolder {

  var rewriteIndex = RewriteIndex()

  def updateRewriteIndex(originalUri: String, rewrittenUri: String, method: Option[Method]): Unit = {
    val invertedIndex = rewriteIndex.inverted + (IndexKey(rewrittenUri, method) → originalUri)
    val forwardIndex = rewriteIndex.forward + (IndexKey(originalUri, method) → rewrittenUri)
    rewriteIndex = RewriteIndex(invertedIndex, forwardIndex)
  }

  def clearIndex(): Unit = {
    rewriteIndex = RewriteIndex()
  }
}
