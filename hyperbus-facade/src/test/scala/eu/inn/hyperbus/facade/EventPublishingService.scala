package eu.inn.hyperbus.facade

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.body
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport.api.{PublishResult, TransportConfigurationLoader, TransportManager}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@body("application/vnd+test-1.json")
case class TestBodyForFacade(content: Obj) extends Body

case class TestRequestForFacade(url: String, body: TestBodyForFacade, messageId: String, correlationId: String) extends StaticGet(body)

class EventPublishingService {
  val requestCounter = new AtomicInteger

  val config = ConfigFactory.load()
  val transportConfiguration = TransportConfigurationLoader.fromConfig(config)
  val transportManager = new TransportManager(transportConfiguration)
  val hyperBus = new HyperBus(transportManager, Some("group1"))
  var subscriptionId: String = null

  def publish (request: TestRequestForFacade): Future[PublishResult] = {
    unsubscribe
    hyperBus <| request
  }

  def subscribeAndPublish(url: String = "/test-response{testPartitionId}", messageId: String = "123", correlationId: String = "456")
                         (optionalTestCallback: (_ => _) = {})= {
    unsubscribe
    subscriptionId = hyperBus |> { request: TestRequestForFacade =>
      Future {
        hyperBus <| TestRequestForFacade(url,
          TestBodyForFacade(Obj(Map("resourceData" → Text(s"Test Event #${requestCounter.incrementAndGet()}")))),
          messageId,
          correlationId)
        optionalTestCallback
      }
    }
  }

  def subscribeAndPublish(requestToReplyWith: TestRequestForFacade)
                         (optionalTestCallback: (_ => _) = {}): Unit = {
    unsubscribe
    subscriptionId = hyperBus |> { request: TestRequestForFacade =>
      Future {
        hyperBus <| requestToReplyWith
        optionalTestCallback
      }
    }
  }

  def unsubscribe() = {
    if(subscriptionId != null) hyperBus.off(subscriptionId)
  }
}
