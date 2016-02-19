package eu.inn.facade

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.Config
import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("application/vnd+test-1.json")
case class FeedTestBody(content: String) extends Body

@request("/test-service/reliable/events")
case class ReliableFeedTestRequest(body: FeedTestBody, headers: Map[String, Seq[String]], messageId: String, correlationId: String) extends StaticPost(body)
with DefinedResponse[Ok[DynamicBody]]

@request("/test-service/unreliable/events")
case class UnreliableFeedTestRequest(body: FeedTestBody, headers: Map[String, Seq[String]], messageId: String, correlationId: String) extends StaticPost(body)
with DefinedResponse[Ok[DynamicBody]]

object TestService extends App {
  def startSeedNode(config: Config): Unit = {
    val serviceConfig = config.getConfig("seed-node-service")
    val system = ActorSystem("eu-inn", serviceConfig)

    Cluster(system)
  }
}

/**
 * NOT THREAD SAFE
 * This class is just a test stuff for publishing events to HyperBus.
 */
class TestService(hyperBus: HyperBus) {
  def publish (request: ReliableFeedTestRequest): Future[PublishResult] = {
    hyperBus <| request
  }

  def publish (request: UnreliableFeedTestRequest): Future[PublishResult] = {
    hyperBus <| request
  }

  def onCommand(uri: Uri, response: Response[Body], optionalTestCallback: (() ⇒ Unit) = () ⇒ ()) = {
    hyperBus.onCommand(uri, Method.GET, None) { request: DynamicRequest ⇒
      Future {
        optionalTestCallback()
        response
      }
    }
  }

  def unsubscribe(subscriptionId: String) = {
    hyperBus.off(subscriptionId)
  }
}

object TestService4WebsocketPerf extends App {

  val config = new ConfigsFactory().config
  val hyperBus = new HyperBusFactory(config).hyperBus
  val testService = new TestService(hyperBus)
  val eventsPerSecond = config.getInt("perf-test.events-per-second")
  var canStart = new AtomicBoolean(false)
  TestService.startSeedNode(config)
  testService.onCommand(Uri("/test-service/unreliable/resource"),
    Ok(DynamicBody(Obj(Map("content" → Text("fullResource"))))), () ⇒ canStart.compareAndSet(false, true))
  while(!canStart.get()) {
    Thread.sleep(1000)
  }
  startLoad()

  def startLoad(): Unit = {
    println("start load")
    while (true) {
      val startTime = System.currentTimeMillis()
      for ( i ← 1 to eventsPerSecond) {
        testService.publish(UnreliableFeedTestRequest(FeedTestBody("perfEvent"), Map(), "messageId", "correlationId"))
      }
      val remainingTime = 1000 - (startTime - System.currentTimeMillis())
      if (remainingTime > 0) Thread.sleep(remainingTime)
    }
  }
}

object TestService4HttpPerf extends App {
  val config = new ConfigsFactory().config
  val hyperBus = new HyperBusFactory(config).hyperBus
  val testService = new TestService(hyperBus)
  TestService.startSeedNode(config)
  testService.onCommand(Uri("/status/test-service"), Ok(DynamicBody(Text("response"))))
}
