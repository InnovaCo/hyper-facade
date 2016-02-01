package eu.inn.facade

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("application/vnd+test-1.json")
case class FeedTestBody(content: String, revisionId: Long = 0) extends Body

@request("/test-service/reliable/{content}/events")
case class ReliableFeedTestRequest(body: FeedTestBody, messageId: String, correlationId: String) extends StaticPost(body)
with DefinedResponse[Ok[DynamicBody]]

@request("/test-service/unreliable/{content}/events")
case class UnreliableFeedTestRequest(body: FeedTestBody, messageId: String, correlationId: String) extends StaticPost(body)
with DefinedResponse[Ok[DynamicBody]]

object TestService extends App {
  val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
  val transportManager = new TransportManager(transportConfiguration)
  val hyperBus = new HyperBus(transportManager, Some("group1"))
  val publishingService = new TestService(new HyperBus(transportManager, Some("group1")))
  val config = ConfigFactory.load()
  startSeedNode()
  publishingService.onCommand(Topic("/test-service"), Ok(DynamicBody(Text("another result"))))

  def startSeedNode(): Unit = {
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
  val requestCounter = new AtomicInteger
  val defaultCallback = { request: ReliableFeedTestRequest => ()}

  var eventSubscriptionId: Option[String] = None
  var commandSubscriptionId: Option[String] = None

  /**
   * Unsubscribes (if already subscribed to) from events and then publishes event passed as an argument
   * @param request request to be published
   * @return future with result of publishing
   */
  def publish (request: ReliableFeedTestRequest): Future[PublishResult] = {
    hyperBus <| request
  }

  /**
   * Unsubscribes (if already subscribed to) from events and then publishes event passed as an argument
   * @param request request to be published
   * @return future with result of publishing
   */
  def publish (request: UnreliableFeedTestRequest): Future[PublishResult] = {
    hyperBus <| request
  }

  def onCommand(topic: Topic, response: Response[Body], optionalTestCallback: (() ⇒ Unit) = () ⇒ ()) = {
    commandSubscriptionId = Some(hyperBus.onCommand(topic, Method.GET, None) { request: DynamicRequest ⇒
      Future {
        optionalTestCallback()
        response
      }
    })
  }

  /**
   * Unsubscribes (if already subscribed to) from events and then subscribes for events of type `TestRequestForFacade`. <br/>
   * Since `EventPublishingService` is notified about new event in queue following will be performed: <br/>
   * 1) it consumes event <br/>
   * 2) unsubscribes from the next events <br/>
   * 3) calls optionalTestCallback() - a function which can be used in tests for adding some required test behavior <br/>
   * 4) publishes request with default body (with content = "ha ha") using macro method `HyperBus.<|` <br/>
   * It is possible to pass custom correlationId and messageId otherwise default ones will be used <br/>
   * @param messageId messageId of event published as a response on incoming event. If not passed "123" + "number of current request" will be used as messageId
   * @param correlationId correlationId of event published as a response on incoming event. If not passed "456" + "number of current request" will be used as correlationId
   * @param optionalTestCallback callback function which will be called when `EventPublishingService` got new event from `HyperBus`. If not passed then
   *                             default one will be used (default callback does nothing)
   */
  def subscribeAndPublishDefaultResponseOnReceived(messageId: String = "123", correlationId: String = "456",
                                                   revisionId: Long = 0, optionalTestCallback: (ReliableFeedTestRequest => Any) = defaultCallback) = {
    unsubscribe(eventSubscriptionId)
    eventSubscriptionId = Some(hyperBus |> { request: ReliableFeedTestRequest =>
      Future {
        unsubscribe(eventSubscriptionId)
        optionalTestCallback(request)
        val requestNumber = requestCounter.incrementAndGet()
        hyperBus <| ReliableFeedTestRequest(
          FeedTestBody("ha ha", revisionId),
          messageId + requestNumber,
          correlationId + requestNumber)
      }
    })
  }

  /**
   * Unsubscribes (if already subscribed to) from events and then subscribes for events of type `TestRequestForFacade`. <br/>
   * Since `EventPublishingService` is notified about new event in queue following will be performed: <br/>
   * 1) it consumes event <br/>
   * 2) unsubscribes from the next events <br/>
   * 3) calls optionalTestCallback() - a function which can be used in tests for adding some required test behavior <br/>
   * 4) publishes request passed as a parameter using macro method `HyperBus.<|` <br/>
   * @param requestToReplyWith request which will be published to `HyperBus` as a response on incoming event
   * @param optionalTestCallback callback function which will be called when `EventPublishingService` got new event from `HyperBus`. If not passed then
   *                             default one will be used (default callback does nothing)
   */
  def subscribeAndPublishOnReceived(requestToReplyWith: ReliableFeedTestRequest, optionalTestCallback: (ReliableFeedTestRequest => Any) = defaultCallback): Unit = {
    unsubscribe(eventSubscriptionId)
    eventSubscriptionId = Some(hyperBus |> { request: ReliableFeedTestRequest =>
      unsubscribe(eventSubscriptionId)
      Future {
        optionalTestCallback(request)
        hyperBus <| requestToReplyWith
      }
    })
  }

  /**
   * Unsubscribes from events in `HyperBus` if already subscribed to
   */
  def unsubscribe(subscriptionId: Option[String]) = {
    if(subscriptionId.isDefined) hyperBus.off(subscriptionId.get)
  }
}

object TestService4Perf extends App {

  val config = new ConfigsFactory().config
  val hyperBus = new HyperBusFactory(config).hyperBus
  val testService = new TestService(hyperBus)
  val eventsPerSecond = config.getInt("perf-test.events-per-second")
  var canStart = new AtomicBoolean(false)
  startSeedNode()
  testService.onCommand(Topic("/test-service/unreliable/resource"),
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
        testService.publish(UnreliableFeedTestRequest(FeedTestBody("perfEvent"), "messageId", "correlationId"))
      }
      val remainingTime = 1000 - (startTime - System.currentTimeMillis())
      if (remainingTime > 0) Thread.sleep(remainingTime)
    }
  }

  def startSeedNode(): Unit = {
    val serviceConfig = config.getConfig("seed-node-service")
    val system = ActorSystem("eu-inn", serviceConfig)

    Cluster(system)
  }
}
