package eu.inn.facade

import eu.inn.facade.raml.RewriteIndexHolder
import org.scalatest.{BeforeAndAfterAll, Suite}

trait CleanRewriteIndex extends BeforeAndAfterAll {
  this: Suite ⇒

  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
  }
}
