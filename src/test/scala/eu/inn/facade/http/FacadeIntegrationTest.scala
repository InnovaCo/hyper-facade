package eu.inn.facade.http

import java.util.concurrent.{SynchronousQueue, TimeUnit, ThreadPoolExecutor, Executor}

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.Config
import eu.inn.binders.dynamic.{Number, Obj, Text}
import eu.inn.facade.modules.{ConfigModule, FiltersModule, ServiceModule}
import eu.inn.facade.{FeedTestBody, FeedTestRequest, TestService}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.standard.Ok
import eu.inn.hyperbus.model.{DynamicBody, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager, Topic}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import scaldi.{Injectable, Injector}
import spray.can.Http
import spray.can.websocket.frame.TextFrame
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise}
import scala.io.Source
import scala.util.Success

class FacadeIntegrationTest extends FreeSpec with Matchers with ScalaFutures with Injectable {
  implicit val injector = getInjector
  implicit val actorSystem = inject[ActorSystem]
  val statusMonitorFacade = inject[StatusMonitorFacade]

  new WsRestServiceApp("localhost", 54321) {
    start {
      pathPrefix("test-service") {
        statusMonitorFacade.statusMonitorRoutes.routes
      }
    }
  }
  val hyperBus = Injectable.inject[HyperBus] // initialize hyperbus

  "Facade integration" - {
    "simple http request" in {

      new TestService(hyperBus).onCommand(Topic("/test-service"), Ok(DynamicBody(Text("response"))))

      // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
      // bound to socket, so we need this stupid timeout to initialize the listener
      Thread.sleep(1000)

      Source.fromURL("http://localhost:8080/test-service", "UTF-8").mkString shouldBe """"response""""
    }

    "websocket: reliable feed" in {

      val host = "localhost"
      val port = 54321
      val url = "/testFilterChain"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))

      val onClientUpgradePromise = Promise[Boolean]()
      val resourceStatePromise = Promise[Boolean]()
      val queuedEventPromise = Promise[Boolean]()
      val publishedEventPromise = Promise[Boolean]()
      val refreshedResourceStatePromise = Promise[Boolean]()
      val afterResubscriptionEventPromise = Promise[Boolean]()

      var clientMessageQueue: mutable.Queue[TextFrame] = mutable.Queue()
      val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        clientMessageQueue = messageQueue
        override def onMessage(frame: TextFrame): Unit = {
          messageQueue += frame
          messageQueue.size match {
          case 1 ⇒ resourceStatePromise.complete (Success (true) )
          case 2 ⇒ queuedEventPromise.complete (Success (true) )
          case 3 ⇒ publishedEventPromise.complete (Success (true) )
          case 4 ⇒ refreshedResourceStatePromise.complete (Success (true) )
          case 5 ⇒ afterResubscriptionEventPromise.complete (Success (true) )
          }
        }

        override def onUpgrade: Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      client ! Connect()  // init websocket connection

      val testService = new TestService(testServiceHyperBus)
      testService.onCommand(Topic("/test-service/resource"),
        Ok(DynamicBody(Obj(Map("content" → Text("fullResource"), "revisionId" → Number(100))))))
      testService.publish(FeedTestRequest(FeedTestBody("haha", 101), "messageId", "correlationId"))

      try {
        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { b ⇒
          client ! DynamicRequest(RequestHeader("/test-service/{content}/events", "subscribe", Some("application/vnd+test-1.json"),
            "messageId", Some("correlationId")), DynamicBody(Obj(Map("content" → Text("haha"), "revisionId" → Number(100)))))
        }

        whenReady(resourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
          val resourceStateMessage = clientMessageQueue.get(0)
          if (resourceStateMessage.isDefined) {
            val resourceState = resourceStateMessage.get.payload.utf8String
            resourceState should startWith ("""{"response":{"status":200,"messageId":""")
            resourceState should endWith ("""body":{"revisionId":100,"content":"fullResource"}}""")
          } else fail("Full resource state wasn't sent to the client")
        }

        whenReady(queuedEventPromise.future,Timeout(Span(5, Seconds))) { b ⇒
          val queuedEventMessage = clientMessageQueue.get(1)
          if (queuedEventMessage.isDefined) {
            val referenceRequest = """{"request":{"url":"/test-service/{content}/events","method":"post","contentType":"application/vnd+test-1.json","messageId":"messageId","correlationId":"correlationId"},"body":{"revisionId":101,"content":"haha"}}"""
            queuedEventMessage.get.payload.utf8String shouldBe referenceRequest
          } else fail("Queued event wasn't sent to the client")
        }

        testService.publish(FeedTestRequest(FeedTestBody("haha", 102), "messageId", "correlationId"))

        whenReady(publishedEventPromise.future,Timeout(Span(5, Seconds))) { b ⇒
          val directEventMessage = clientMessageQueue.get(2)
          if (directEventMessage.isDefined) {
            val referenceRequest = """{"request":{"url":"/test-service/{content}/events","method":"post","contentType":"application/vnd+test-1.json","messageId":"messageId","correlationId":"correlationId"},"body":{"revisionId":102,"content":"haha"}}"""
            directEventMessage.get.payload.utf8String shouldBe referenceRequest
          } else fail("Last event wasn't sent to the client")
        }

        testService.onCommand(Topic("/test-service/resource"),
          Ok(DynamicBody(Obj(Map("content" → Text("fullResource"), "revisionId" → Number(106))))))
        testService.publish(FeedTestRequest(FeedTestBody("updateFromFuture", 105), "messageId2", "correlationId2"))

        whenReady(refreshedResourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
          val resourceStateMessage = clientMessageQueue.get(3)
          if (resourceStateMessage.isDefined) {
            val resourceState = resourceStateMessage.get.payload.utf8String
            resourceState should startWith ("""{"response":{"status":200,"messageId":""")
            resourceState should endWith ("""body":{"revisionId":106,"content":"fullResource"}}""")
          } else fail("Full resource state wasn't sent to the client")
        }

        testService.publish(FeedTestRequest(FeedTestBody("haha", 107), "messageId", "correlationId"))

        whenReady(afterResubscriptionEventPromise.future,Timeout(Span(5, Seconds))) { b ⇒
          val directEventMessage = clientMessageQueue.get(4)
          if (directEventMessage.isDefined) {
            val referenceRequest = """{"request":{"url":"/test-service/{content}/events","method":"post","contentType":"application/vnd+test-1.json","messageId":"messageId","correlationId":"correlationId"},"body":{"revisionId":107,"content":"haha"}}"""
            directEventMessage.get.payload.utf8String shouldBe referenceRequest
          } else fail("Last event wasn't sent to the client")
        }
      } catch {
        case ex: Throwable ⇒
          fail(ex)
      } finally {
        actorSystem.shutdown()
        actorSystem.awaitTermination()
      }
    }
  }

  def upgradeHeaders(host: String, port: Int) = List(
    HttpHeaders.Host(host, port),
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate"))

  def getInjector: Injector = {
    val filtersModule = new FiltersModule
    val injector = new ConfigModule :: filtersModule :: new ServiceModule
    filtersModule.initOuterBindings
    injector.initNonLazy()
    injector
  }

  def testServiceHyperBus: HyperBus = {
    val config = inject[Config]
    val testServiceTransportMgr = new TransportManager(TransportConfigurationLoader.fromConfig(config))
    val hypeBusGroupKey = "hyperbus.transports.kafka-server.defaultGroupName"
    val defaultHyperBusGroup = if (config.hasPath(hypeBusGroupKey)) Some(config.getString(hypeBusGroupKey)) else None
    new HyperBus(testServiceTransportMgr, defaultHyperBusGroup)(ExecutionContext.fromExecutor(newPoolExecutor()))
  }

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5*60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }
}
