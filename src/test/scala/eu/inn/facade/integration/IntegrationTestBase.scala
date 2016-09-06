package eu.inn.facade.integration

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import eu.inn.auth.BasicAuthenticationService
import eu.inn.facade.model.{UriSpecificDeserializer, UriSpecificSerializer}
import eu.inn.facade.modules.TestInjectors
import eu.inn.facade.workers.{HttpWorker, TestWsRestServiceApp, WsTestClientHelper}
import eu.inn.facade.{FacadeConfigPaths, TestBase, TestService}
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.transport.api.Subscription
import eu.inn.servicecontrol.api.Service
import org.scalatest.BeforeAndAfterEach
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration._

class IntegrationTestBase(val ramlFilePath: String) extends TestBase
  with BeforeAndAfterEach with WsTestClientHelper {

  System.setProperty(FacadeConfigPaths.RAML_FILE, ramlFilePath)

  implicit val injector = TestInjectors()
  implicit val actorSystem = inject[ActorSystem]
  implicit val patience = PatienceConfig(scaled(Span(10, Seconds)))
  implicit val timeout = akka.util.Timeout(15.seconds)
  implicit val uid = new UriSpecificDeserializer
  implicit val uis = new UriSpecificSerializer

  val httpWorker = inject[HttpWorker]

  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]
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

  def register(s: Subscription) = {
    subscriptions += s
  }
}

