package eu.inn.hyperbus.facade

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("application/vnd+test-1.json")
case class TestBodyForFacade(content: String) extends Body

@request("/test-facade/{content}")
case class TestRequestForFacade(body: TestBodyForFacade, messageId: String, correlationId: String) extends StaticGet(body)

class EventPublishingService {
  val requestCounter = new AtomicInteger

  val config = ConfigFactory.load()
  val transportConfiguration = TransportConfigurationLoader.fromConfig(config)
  val transportManager = new TransportManager(transportConfiguration)
  val hyperBus = new HyperBus(transportManager, Some("group1"))
  var subscriptionId: String = null
  val defaultCallback = {() => println("default")}

  def publish (request: TestRequestForFacade): Future[PublishResult] = {
    unsubscribe
    hyperBus <| request
  }

  def subscribeAndPublishDefaultResponseOnReceived(messageId: String = "123", correlationId: String = "456", optionalTestCallback: (() => _) = defaultCallback)= {
    unsubscribe
    subscriptionId = hyperBus |> { request: TestRequestForFacade =>
      unsubscribe
      Future {
        optionalTestCallback()
        hyperBus <| TestRequestForFacade(
          TestBodyForFacade("ha ha"),
          messageId,
          correlationId)
      }
    }
  }

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

  def unsubscribe() = {
    if(subscriptionId != null) hyperBus.off(subscriptionId)
  }
}
