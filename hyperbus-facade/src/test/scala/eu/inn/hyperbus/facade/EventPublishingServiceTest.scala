package eu.inn.hyperbus.facade

import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Promise, Await, Future}
import scala.util.Success

/**
 * This tests requires Kafka up and running
 */
class EventPublishingServiceTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter with BeforeAndAfterAll {
  val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
  val transportManager = new TransportManager(transportConfiguration)
  val publishingService = new EventPublishingService
  var hyperBus = new HyperBus(transportManager, Some("group1"))
  
  after {
    publishingService.unsubscribe
  }

  override def afterAll(): Unit = {
    val timeout = 10.seconds
    try {
      Await.result(hyperBus.shutdown(timeout), timeout)
    } catch {
      case t: Throwable ⇒ ()
    }
  }
  
  "EventPublishingService " - {
    "publish" in {
      val request = TestRequestForFacade(TestBodyForFacade("ha ha"),
        "123",
        "456")
      val receiveFuture = Future {}
      val subscriptionId = hyperBus |> { request: TestRequestForFacade =>
        receiveFuture
      }

      publishingService.publish(request)
      whenReady(receiveFuture, Timeout(Span(5, Seconds))) { _ => }
      hyperBus.off(subscriptionId)
    }

    "subscribeAndPublishOnReceived" in {
      val initialRequest = TestRequestForFacade(TestBodyForFacade("ha ha"),
        "requestMessage",
        "requestCorrelationId")
      val requestToReplyWith = TestRequestForFacade(TestBodyForFacade("ha ha"),
        "responseMessage",
        "responseCorrelationId")
      var echoRequest: TestRequestForFacade = null
      val onEchoReceived: Promise[Unit] = Promise()
      var subscriptionId: String = null
      val onReceive = { () =>
        subscriptionId = hyperBus |> { request: TestRequestForFacade =>
          onEchoReceived.success({echoRequest = request})
          onEchoReceived.future
        }
      }

      publishingService.subscribeAndPublishOnReceived(requestToReplyWith, onReceive)
      whenReady(hyperBus <| initialRequest, Timeout(Span(5, Seconds))) { _ =>
        whenReady(onEchoReceived.future, Timeout(Span(5, Seconds))) { _ =>
          echoRequest should equal(requestToReplyWith)
        }
      }
      hyperBus.off(subscriptionId)
    }

    "subscribeAndPublishDefaultResponseOnReceived" in {
      val initialRequest = TestRequestForFacade(TestBodyForFacade("ha ha"),
        "requestMessage",
        "requestCorrelationId")
      val expectedEchoRequest = TestRequestForFacade(TestBodyForFacade("ha ha"),
        "responseMessage",
        "responseCorrelationId")
      var echoRequest: TestRequestForFacade = null
      val onEchoReceived: Promise[Unit] = Promise()
      var subscriptionId: String = null
      val onReceive = { () =>
        subscriptionId = hyperBus |> { request: TestRequestForFacade =>
          onEchoReceived.success({echoRequest = request})
          onEchoReceived.future
        }
      }

      publishingService.subscribeAndPublishDefaultResponseOnReceived("responseMessage", "responseCorrelationId", onReceive)
      whenReady(hyperBus <| initialRequest, Timeout(Span(5, Seconds))) { _ =>
        whenReady(onEchoReceived.future, Timeout(Span(5, Seconds))) { _ =>
          echoRequest should equal(expectedEchoRequest)
        }
      }
      hyperBus.off(subscriptionId)
    }
  }
}
