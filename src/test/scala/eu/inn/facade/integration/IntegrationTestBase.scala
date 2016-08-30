package eu.inn.facade.integration

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import eu.inn.auth.BasicAuthenticationService
import eu.inn.facade.workers.{HttpWorker, WsRestServiceApp, WsTestClientHelper}
import eu.inn.facade.model.{UriSpecificDeserializer, UriSpecificSerializer}
import eu.inn.facade.modules.Injectors
import eu.inn.facade.{CleanRewriteIndex, FacadeConfigPaths, TestService}
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.transport.api.Subscription
import eu.inn.servicecontrol.api.Service
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.duration._

class IntegrationTestBase(val ramlFilePath: String) extends FreeSpec with Matchers with ScalaFutures with CleanRewriteIndex with Injectable
  with BeforeAndAfterEach with BeforeAndAfterAll with WsTestClientHelper {

  System.setProperty(FacadeConfigPaths.RAML_FILE, ramlFilePath)

  implicit val injector = Injectors()
  implicit val actorSystem = inject[ActorSystem]
  implicit val patience = PatienceConfig(scaled(Span(15, Seconds)))
  implicit val timeout = akka.util.Timeout(15.seconds)
  implicit val uid = new UriSpecificDeserializer
  implicit val uis = new UriSpecificSerializer

  val httpWorker = inject[HttpWorker]

  val app = inject[Service].asInstanceOf[WsRestServiceApp]
  app.start {
    httpWorker.restRoutes.routes
  }

  inject[BasicAuthenticationService]
  val hyperbus = inject[Hyperbus] // initialize hyperbus
  val testService = inject[TestService]
  val subscriptions = scala.collection.mutable.MutableList[Subscription]()

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(1000)

  def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  override def afterEach(): Unit = {
    subscriptions.foreach(hyperbus.off)
    subscriptions.clear
  }

  override def afterAll(): Unit = {
    app.stopService(true)
  }

  def register(s: Subscription) = {
    subscriptions += s
  }
}

