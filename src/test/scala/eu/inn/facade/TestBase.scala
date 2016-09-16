package eu.inn.facade

import eu.inn.facade.raml.RewriteIndexHolder
import eu.inn.facade.workers.TestWsRestServiceApp
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable

abstract class TestBase extends FreeSpec with Matchers with ScalaFutures with Injectable with MockContext with BeforeAndAfterAll {

  def app: TestWsRestServiceApp
  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
    app.shutdown()
  }
}
