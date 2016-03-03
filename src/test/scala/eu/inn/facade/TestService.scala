package eu.inn.facade

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.Config
import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("application/vnd+test-1.json")
case class FeedTestBody(content: String) extends Body

@request(Method.FEED_POST, "/test-service/reliable/feed")
case class ReliableFeedTestRequest(body: FeedTestBody, headers: Map[String, Seq[String]]) extends Request[FeedTestBody]
  with DefinedResponse[Ok[DynamicBody]]

@request(Method.FEED_POST, "/test-service/unreliable/feed")
case class UnreliableFeedTestRequest(body: FeedTestBody, headers: Map[String, Seq[String]]) extends Request[FeedTestBody]
  with DefinedResponse[Ok[DynamicBody]]

object TestService extends App {
  val config = new ConfigsFactory().config
  val hyperBus = new HyperBusFactory(config).hyperBus
  val testService = new TestService(hyperBus)
  startSeedNode(config)
  testService.onCommand(RequestMatcher(Some(Uri("/aaa")), Map(Header.METHOD → Specific(Method.GET))),
    Ok(DynamicBody(Text("response"))))

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
  def publish(request: ReliableFeedTestRequest): Future[PublishResult] = {
    hyperBus <| request
  }

  def publish(request: UnreliableFeedTestRequest): Future[PublishResult] = {
    println(s"event published ${System.currentTimeMillis()} $request")
    hyperBus <| request
  }

  def onCommand(matcher: RequestMatcher, response: Response[Body], optionalTestCallback: (() ⇒ Unit) = () ⇒ ()) = {
    hyperBus.onCommand(matcher) { request: DynamicRequest ⇒
      Future {
        optionalTestCallback()
        response
      }
    }
  }

  def unsubscribe(subscription: Subscription) = {
    hyperBus.off(subscription)
  }
}

object TestService4WebsocketPerf extends App {

  val config = new ConfigsFactory().config
  val hyperBus = new HyperBusFactory(config).hyperBus
  val testService = new TestService(hyperBus)
  val eventsPerSecond = config.getInt("perf-test.events-per-second")
  var canStart = new AtomicBoolean(false)
  TestService.startSeedNode(config)
  testService.onCommand(RequestMatcher(Some(Uri("/test-service/unreliable/resource")), Map(Header.METHOD → Specific("subscribe"))),
    Ok(DynamicBody(Obj(Map("content" → Text("fullResource"))))), () ⇒ canStart.compareAndSet(false, true))
  while (!canStart.get()) {
    Thread.sleep(1000)
  }
  startLoad()

  def startLoad(): Unit = {
    println("start load")
    while (true) {
      val startTime = System.currentTimeMillis()
      for (i ← 1 to eventsPerSecond) {
        testService.publish(UnreliableFeedTestRequest(FeedTestBody("perfEvent"), Headers()))
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
  testService.onCommand(RequestMatcher(Some(Uri("/test-service/unreliable/resource")), Map(Header.METHOD → Specific(Method.GET))),
    Ok(DynamicBody(Text("response"))))
}
