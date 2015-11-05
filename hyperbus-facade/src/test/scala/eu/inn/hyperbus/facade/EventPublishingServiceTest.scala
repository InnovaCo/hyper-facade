package eu.inn.hyperbus.facade

import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

/**
 * This tests requires Kafka up and running
 */
class EventPublishingServiceTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter with BeforeAndAfterAll {
  val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
  val transportManager = new TransportManager(transportConfiguration)
  val publishingService = new EventPublishingService
  val hyperBus = new HyperBus(transportManager, Some("group1"))
  
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
      val request = TestRequestForFacade("/test-request{testPartitionId}",
        TestBodyForFacade(Obj(Map("resourceData" → Text("Test Event")))),
        "123",
        "456")
      val receiveFuture = Future {}
      hyperBus |> {request: TestRequestForFacade =>
        receiveFuture
      }
      
      publishingService.publish(request)
      whenReady(receiveFuture, Timeout(Span(5, Seconds))) { _ => }
    }

    "subscribeAndPublish" in {
      val initialRequest = TestRequestForFacade("/test-request{testPartitionId}",
        TestBodyForFacade(Obj(Map("resourceData" → Text("Test Event")))),
        "requestMessage",
        "requestCorrelationId")
      val requestToReplyWith = TestRequestForFacade("/test-request{testPartitionId}",
        TestBodyForFacade(Obj(Map("resourceData" → Text("Test Event")))),
        "responseMessage",
        "responseCorrelationId")
      var echoRequest: TestRequestForFacade = null
      var onEchoReceivedFuture: Future[Unit] = null
      val onReceive: (Unit => String) = {() =>
        hyperBus |> {request: TestRequestForFacade =>
          onEchoReceivedFuture = Future {
            echoRequest = request
          }
          onEchoReceivedFuture
        }
      }

      publishingService.subscribeAndPublish(requestToReplyWith) (onReceive)
      hyperBus <| initialRequest
      whenReady(onEchoReceivedFuture, Timeout(Span(5, Seconds))) { _ =>
        echoRequest should equal(requestToReplyWith)
      }
    }
  }
}
