package eu.inn.facade

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.Config
import eu.inn.binders.value.{Obj, Text}
import eu.inn.config.ConfigLoader
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("feed-test")
case class FeedTestBody(content: String) extends Body

@request(Method.FEED_POST, "/resource/reliable-feed")
case class ReliableFeedTestRequest(body: FeedTestBody, headers: Map[String, Seq[String]]) extends Request[FeedTestBody]

@request(Method.FEED_POST, "/resource/unreliable-feed")
case class UnreliableFeedTestRequest(body: FeedTestBody, headers: Map[String, Seq[String]]) extends Request[FeedTestBody]

@request(Method.FEED_PUT, "/rewritten-resource/{serviceId}")
case class UnreliableRewriteFeedTestRequest(serviceId: String, body: FeedTestBody, headers: Map[String, Seq[String]]) extends Request[FeedTestBody]

@request(Method.FEED_PUT, "/rewritten-events/{path:*}")
case class RewriteOutsideFeedTestRequest(path: String, body: DynamicBody, headers: Map[String, Seq[String]]) extends Request[DynamicBody]


object TestService extends App {
  val config = ConfigLoader()
  val hyperbus = new HyperbusFactory(config).hyperbus
  val testService = new TestService(hyperbus)
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
  * This class is just a test stuff for publishing events to Hyperbus.
  */
class TestService(hyperbus: Hyperbus) {
  def publish(request: Request[Body]): Future[PublishResult] = {
    hyperbus <| request
  }

  def onCommand(matcher: RequestMatcher, response: Response[Body], optionalTestCallback: (DynamicRequest ⇒ Unit) = _ ⇒ ()) = {
    hyperbus.onCommand(matcher) { request: DynamicRequest ⇒
      Future {
        optionalTestCallback(request)
        response
      }
    }
  }

  def onCommand1(matcher: RequestMatcher, response:(() ⇒ Response[Body]), optionalTestCallback: (DynamicRequest ⇒ Unit) = _ ⇒ ()) = {
    hyperbus.onCommand(matcher) { request: DynamicRequest ⇒
      Future {
        optionalTestCallback(request)
        response()
      }
    }
  }

  def unsubscribe(subscription: Subscription) = {
    hyperbus.off(subscription)
  }
}

object TestService4WebsocketPerf extends App {
  val RESOURCE_URI = Uri("/perf-test-service/ws")
  val config = ConfigLoader()
  val hyperbus = new HyperbusFactory(config).hyperbus
  val testService = new TestService(hyperbus)
  val eventsPerSecond = config.getInt("perf-test.events-per-second")
  var canStart = new AtomicBoolean(false)
  testService.onCommand(RequestMatcher(Some(RESOURCE_URI), Map(Header.METHOD → Specific("get"))),
    Ok(DynamicBody(Obj(Map("content" → Text("fullResource"))))), { r ⇒
      canStart.compareAndSet(false, true)
    })
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

@body("perf-test")
case class PerfTestEventBody(content: String, secretField: String) extends Body

@request(Method.FEED_PUT, "/perf-test")
case class PerfTestFeedEvent(body: PerfTestEventBody, headers: Map[String, Seq[String]]) extends Request[PerfTestEventBody]

object TestService4WebsocketPerfWithFilters extends App {
  val RESOURCE_URI = Uri("/perf-test")  // this uri should match value in perf-test.raml as rewritten uri for "/perf-test-with-filters"
  val config = ConfigLoader()
  val hyperbus = new HyperbusFactory(config).hyperbus
  val testService = new TestService(hyperbus)
  val eventsPerSecond = config.getInt("perf-test.events-per-second")
  var canStart = new AtomicBoolean(false)
  var revision = 1
  testService.onCommand1(RequestMatcher(Some(RESOURCE_URI), Map(Header.METHOD → Specific("get"))),
    state, { _ ⇒
      canStart.compareAndSet(false, true)
    })
  while (!canStart.get()) {
    Thread.sleep(1000)
  }
  startLoad()

  def startLoad(): Unit = {
    println("start load")
    while (true) {
      val startTime = System.currentTimeMillis()
      for (i ← 1 to eventsPerSecond) {
        revision += 1
        println(revision)
        testService.publish(PerfTestFeedEvent(PerfTestEventBody("perfEvent", "secret"), Headers.plain(Map("revision" → Seq(s"$revision"), "messageId" → Seq("m")))))
      }
      val remainingTime = 1000 - (startTime - System.currentTimeMillis())
      if (remainingTime > 0) Thread.sleep(remainingTime)
    }
  }

  def state(): Response[Body] = {
    println(s"here $revision")
    Ok(DynamicBody(Obj(Map("content" → Text("fullResource")))), Headers.plain(Map("revision" → Seq(s"$revision"), "messageId" → Seq("m"))))
  }
}

object TestService4HttpPerf extends App {
  val RESOURCE_URI = Uri("/resource/state")  // this uri should match value in perf-test.raml as rewritten uri for "/perf-test-service/http"
  val config = ConfigLoader()
  val hyperbus = new HyperbusFactory(config).hyperbus
  val testService = new TestService(hyperbus)
  testService.onCommand(RequestMatcher(Some(RESOURCE_URI), Map(Header.METHOD → Specific(Method.GET))),
    Ok(DynamicBody(Text("response"))))
}
