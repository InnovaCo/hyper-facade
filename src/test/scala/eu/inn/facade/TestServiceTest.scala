package eu.inn.facade

import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

/**
 * These tests require Kafka up and running
 */
class TestServiceTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter with BeforeAndAfterAll {
  val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
  val transportManager = new TransportManager(transportConfiguration)
  val hyperBus = new HyperBus(transportManager, Some("group1"))
  val publishingService = new TestService(new HyperBus(transportManager, Some("group1")))
  
  after {
    publishingService.unsubscribe()
  }

  override def afterAll(): Unit = {
    val timeout = 10.seconds
    try {
      Await.result(hyperBus.shutdown(timeout), timeout)
    } catch {
      case t: Throwable ⇒ ()
    }
  }
  
  "TestServiceForFacade " - {
    "publish" in {
      val request = FeedTestRequest(FeedTestBody("ha ha"),
        "123",
        "456")
      val receiveFuture = Future {}
      val subscriptionId = hyperBus |> { request: FeedTestRequest =>
        receiveFuture
      }

      publishingService.publish(request)
      whenReady(receiveFuture, Timeout(Span(5, Seconds))) { _ => }
      hyperBus.off(subscriptionId)
    }

    "subscribeAndPublishOnReceived" in {
      val initialRequest = FeedTestRequest(FeedTestBody("ha ha"),
        "requestMessage",
        "requestCorrelationId")
      val requestToReplyWith = FeedTestRequest(FeedTestBody("ha ha"),
        "responseMessage",
        "responseCorrelationId")
      var echoRequest: FeedTestRequest = null
      val onEchoReceived: Promise[Unit] = Promise()
      var subscriptionId: String = null
      val onReceive = { received: FeedTestRequest =>
        subscriptionId = hyperBus |> { request: FeedTestRequest =>
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
      val initialRequest = FeedTestRequest(FeedTestBody("ha ha"),
        "requestMessage",
        "requestCorrelationId")
      val expectedEchoRequest = FeedTestRequest(FeedTestBody("ha ha"),
        "responseMessage1",
        "responseCorrelationId1")
      var echoRequest: FeedTestRequest = null
      val onEchoReceived: Promise[Unit] = Promise()
      var subscriptionId: String = null
      val onReceive = {received: FeedTestRequest =>
        subscriptionId = hyperBus |> { request: FeedTestRequest =>
          onEchoReceived.success({echoRequest = request})
          onEchoReceived.future
        }
      }

      publishingService.subscribeAndPublishDefaultResponseOnReceived("responseMessage", "responseCorrelationId", 0, onReceive)
      whenReady(hyperBus <| initialRequest, Timeout(Span(5, Seconds))) { _ =>
        whenReady(onEchoReceived.future, Timeout(Span(5, Seconds))) { _ =>
          echoRequest should equal(expectedEchoRequest)
        }
      }
      hyperBus.off(subscriptionId)
    }
  }
}
