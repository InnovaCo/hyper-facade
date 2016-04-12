package eu.inn.facade.raml

object RewriteIndexHolder {

  var rewriteIndex: Option[RewriteIndex] = None

  def updateRewriteIndex(originalUri: String, rewrittenUri: String, method: Option[Method]): Unit = {
    val (oldInvertedIndex, oldForwardIndex) = rewriteIndex match {
      case Some(index) ⇒ (index.inverted, index.forward)
      case None ⇒ (Map.empty[(Option[Method], String), String], Map.empty[(Option[Method], String), String])
    }
    val invertedIndex = oldInvertedIndex + ((method, rewrittenUri) → originalUri)
    val forwardIndex = oldForwardIndex + ((method, originalUri) → rewrittenUri)
    rewriteIndex = Some(RewriteIndex(invertedIndex, forwardIndex))
  }
}
