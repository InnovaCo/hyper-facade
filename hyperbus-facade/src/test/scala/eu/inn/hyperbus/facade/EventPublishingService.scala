package eu.inn.hyperbus.facade

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport.api._
import eu.inn.util.ConfigComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("application/vnd+test-1.json")
case class TestBodyForFacade(content: String) extends Body

@request("/test-facade/{content}")
case class TestRequestForFacade(body: TestBodyForFacade, messageId: String, correlationId: String) extends StaticGet(body)
with DefinedResponse[Ok[DynamicBody]]

object EventPublishingService extends App with ConfigComponent {
  val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
  val transportManager = new TransportManager(transportConfiguration)
  val hyperBus = new HyperBus(transportManager, Some("group1"))
  val publishingService = new EventPublishingService(new HyperBus(transportManager, Some("group1")))
  startSeedNode
  publishingService.onCommand()

  def startSeedNode: Unit = {
    val serviceConfig = config.getConfig("seed-node-service")
    val system = ActorSystem("eu-inn", serviceConfig)

    Cluster(system)
  }
}

/**
 * NOT THREAD SAFE
 * This class is just a test stuff for publishing events to HyperBus. It operates just with requests of type `TestRequestForFacade`
 */
class EventPublishingService(hyperBus: HyperBus) {
  val requestCounter = new AtomicInteger
  val defaultCallback = {() => println("default")}

  var subscriptionId: String = null

  /**
   * Unsubscribes (if already subscribed to) from events and then publishes event passed as an argument
   * @param request request to be published
   * @return future with result of publishing
   */
  def publish (request: TestRequestForFacade): Future[PublishResult] = {
    unsubscribe
    hyperBus <| request
  }

  def onCommand(): String = {
    hyperBus.onCommand(Topic("/test-facade"), Method.GET, None) { request: DynamicRequest ⇒
      Future {
        Ok(DynamicBody(Text("another result")))
      }
    }
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
  def subscribeAndPublishDefaultResponseOnReceived(messageId: String = "123", correlationId: String = "456", optionalTestCallback: (() => _) = defaultCallback)= {
    unsubscribe
    subscriptionId = hyperBus |> { request: TestRequestForFacade =>
      unsubscribe
      Future {
        optionalTestCallback()
        val requestNumber = requestCounter.incrementAndGet()
        hyperBus <| TestRequestForFacade(
          TestBodyForFacade("ha ha"),
          messageId + requestNumber,
          correlationId + requestNumber)
      }
    }
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
  def subscribeAndPublishOnReceived(requestToReplyWith: TestRequestForFacade, optionalTestCallback: (() => _) = defaultCallback): Unit = {
    unsubscribe
    subscriptionId = hyperBus |> { request: TestRequestForFacade =>
      unsubscribe
      Future {
        optionalTestCallback()
        hyperBus <| requestToReplyWith
      }
    }
  }

  /**
   * Unsubscribes from events in `HyperBus` if already subscribed to
   */
  def unsubscribe() = {
    if(subscriptionId != null) hyperBus.off(subscriptionId)
  }
}
