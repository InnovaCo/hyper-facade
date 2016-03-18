package eu.inn.facade.http

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.Config
import eu.inn.binders.dynamic.{Null, Obj, Text}
import eu.inn.facade.model.{FacadeHeaders, FacadeRequest}
import eu.inn.facade.modules.Injectors
import eu.inn.facade.{FeedTestBody, ReliableFeedTestRequest, TestService, UnreliableFeedTestRequest}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.api.{Subscription, TransportConfigurationLoader, TransportManager}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FreeSpec, Matchers}
import scaldi.Injectable
import spray.can.Http
import spray.can.websocket.frame.TextFrame
import spray.client.pipelining._
import spray.http
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.{RawHeader, `Content-Type`}
import spray.http._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.io.Source
import scala.util.{Success, Try}


/**
  * Important: Kafka should be up and running to pass this test
  */
class FacadeIntegrationTest extends FreeSpec with Matchers with ScalaFutures with Injectable with BeforeAndAfterEach {
  implicit val injector = Injectors()
  implicit val actorSystem = inject[ActorSystem]
  val statusMonitorFacade = inject[HttpWorker]

  new WsRestServiceApp("localhost", 54321) {
    start {
      statusMonitorFacade.restRoutes.routes
    }
  }
  val hyperBus = inject[HyperBus] // initialize hyperbus
  val testService = new TestService(testServiceHyperBus)
  val subscriptions = scala.collection.mutable.MutableList[Subscription]()

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(1000)

  "Facade integration" - {
    "http get. Resource configured in RAML" in {
      testService.onCommand(RequestMatcher(Some(Uri("/status/test-service")), Map(Header.METHOD → Specific(Method.GET))),
        Ok(DynamicBody(Text("response"))), { request ⇒
          request.uri shouldBe Uri("/status/test-service")
          request.body shouldBe DynamicBody(Obj(Map("emptyParam" → Null, "param" → Text("1"))))
        }
      ) onSuccess {
        case subscr ⇒ register(subscr)
      }

      Source.fromURL("http://localhost:54321/status/test-service?param=1&emptyParam=", "UTF-8").mkString shouldBe """"response""""
    }

    "http get. Resource is not configured in RAML" in {
      testService.onCommand(RequestMatcher(Some(Uri("/someSecretResource")), Map(Header.METHOD → Specific(Method.GET))),
        Ok(DynamicBody(Text("response"))), { request ⇒
          request.uri shouldBe Uri("/someSecretResource")
          request.body shouldBe DynamicBody(Obj(Map("emptyParam" → Null, "param" → Text("1"))))
        }
      ) onSuccess {
        case subscr ⇒ register(subscr)
      }

      Source.fromURL("http://localhost:54321/someSecretResource?param=1&emptyParam=", "UTF-8").mkString shouldBe """"response""""
    }

    "http get. Error response" in {
      testService.onCommand(RequestMatcher(Some(Uri("/failedResource")), Map(Header.METHOD → Specific(Method.GET))),
        ServiceUnavailable(ErrorBody("service_is_not_available", Some("No connection to DB")))) onSuccess {
        case subscr ⇒ register(subscr)
      }

      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val uri404 = "http://localhost:54321/test-service/reliable"
      val responseFuture404 = pipeline(Get(http.Uri(uri404)))
      whenReady(responseFuture404, Timeout(Span(5, Seconds))) { response ⇒
        response.entity.asString should include (""""code":"not_found"""")
        response.status.intValue shouldBe 404
      }

      val uri503 = "http://localhost:54321/failedResource"
      val responseFuture503 = pipeline(Get(http.Uri(uri503)))
      whenReady(responseFuture503, Timeout(Span(5, Seconds))) { response ⇒
        response.entity.asString should include (""""code":"service_is_not_available"""")
        response.status.intValue shouldBe 503

        subscriptions.foreach(hyperBus.off)
        subscriptions.clear
        Await.result(clientActorSystem.terminate(), Duration.Inf)
      }
    }

    "http get reliable resource. Check hyperbus-revision" in {
      testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
        Ok(DynamicBody(Text("response")), Headers(Map(Header.REVISION → Seq("1"), Header.CONTENT_TYPE → Seq("user-profile"))))) onSuccess {
        case subscr ⇒ register(subscr)
      }

      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val uri = "http://localhost:54321/test-service/reliable"
      val responseFuture = pipeline(Get(http.Uri(uri)))
      whenReady(responseFuture, Timeout(Span(5, Seconds))) { response ⇒
        response.entity.asString shouldBe """"response""""
        response.headers should contain (RawHeader("hyper-bus-revision", "1"))

        val mediaType = MediaTypes.register(MediaType.custom("application", "vnd.user-profile+json", true, false))
        val contentType = ContentType(mediaType, `UTF-8`)
        response.headers should contain (`Content-Type`(contentType))

        subscriptions.foreach(hyperBus.off)
        subscriptions.clear
        Await.result(clientActorSystem.terminate(), Duration.Inf)
      }
    }

    "websocket: unreliable feed" in {

      val host = "localhost"
      val port = 54321
      val url = "/status/test-service"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))

      val onClientUpgradePromise = Promise[Boolean]()
      val resourceStatePromise = Promise[Boolean]()
      val publishedEventPromise = Promise[Boolean]()

      var clientMessageQueue: mutable.Queue[TextFrame] = mutable.Queue()
      val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
          clientMessageQueue += frame
          clientMessageQueue.size match {
            case 1 ⇒ resourceStatePromise.complete(Success(true))
            case 2 ⇒ publishedEventPromise.complete(Success(true))
            case other: Int ⇒ clientMessageQueue.foreach(frame ⇒ println(frame.payload.utf8String))
          }
        }

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "unreliable-feed-client")

      client ! Connect() // init websocket connection

      testService.onCommand(RequestMatcher(Some(Uri("/test-service/unreliable")), Map(Header.METHOD → Specific(Method.GET))),
        Ok(DynamicBody(Obj(Map("content" → Text("fullResource")))))) onSuccess {
        case subscr ⇒ register(subscr)
      }

      whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        client ! FacadeRequest(Uri("/test-service/unreliable"), "subscribe",
          Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
            FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
            FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
          Obj(Map("content" → Text("haha"))))
      }

      whenReady(resourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val resourceStateMessage = clientMessageQueue.get(0)
        if (resourceStateMessage.isDefined) {
          val resourceState = resourceStateMessage.get.payload.utf8String
          resourceState should startWith("""{"status":200,"headers":{"hyperBusMessageId":""")
          resourceState should endWith("""body":{"content":"fullResource"}}""")
        } else fail("Full resource state wasn't sent to the client")
      }

      testService.publish(
        UnreliableFeedTestRequest(
          FeedTestBody("haha"),
          Headers.plain(Map(
            Header.MESSAGE_ID → Seq("messageId"),
            Header.CORRELATION_ID → Seq("correlationId")))))

      whenReady(publishedEventPromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val eventMessage = clientMessageQueue.get(1)
        if (eventMessage.isDefined) {
          val referenceRequest = """{"uri":"/test-service/unreliable","method":"feed:post","headers":{"hyperBusMessageId":["messageId"],"hyperBusCorrelationId":["correlationId"],"contentType":["application/vnd.feed-test+json"]},"body":{"content":"haha"}}"""
          eventMessage.get.payload.utf8String shouldBe referenceRequest
        } else fail("Event wasn't sent to the client")
      }

      client ! FacadeRequest(Uri("/test-service/unreliable"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)
    }

    "websocket: handle error response" in {
      val host = "localhost"
      val port = 54321
      val url = "/status/test-service"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))

      val onClientUpgradePromise = Promise[Boolean]()
      val resourceStatePromise = Promise[Boolean]()

      var clientMessageQueue: mutable.Queue[TextFrame] = mutable.Queue()
      val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
          clientMessageQueue += frame
          clientMessageQueue.size match {
            case 1 ⇒ resourceStatePromise.complete(Success(true))
            case 2 ⇒ fail("there should not be incoming events after error response")
          }
        }

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "error-feed-client")

      client ! Connect() // init websocket connection

      testService.onCommand(RequestMatcher(Some(Uri("/test-service/unreliable")), Map(Header.METHOD → Specific(Method.GET))),
        eu.inn.hyperbus.model.InternalServerError(ErrorBody("unhandled_exception", Some("Internal server error"), errorId = "123")))

      whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        client ! FacadeRequest(Uri("/test-service/unreliable"), "subscribe",
            Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
              FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
              FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
            Obj(Map("content" → Text("haha"))))
      }

      whenReady(resourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val resourceStateMessage = clientMessageQueue.get(0)
        if (resourceStateMessage.isDefined) {
          val resourceState = resourceStateMessage.get.payload.utf8String
          resourceState should startWith ("""{"status":500,"headers":""")
          resourceState should include (""""code":"unhandled_exception"""")
        } else fail("Full resource state wasn't sent to the client")
      }
    }

    "websocket: reliable feed" in {

      val host = "localhost"
      val port = 54321
      val url = "/status/test-service"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))

      val onClientUpgradePromise = Promise[Boolean]()
      val resourceStatePromise = Promise[Boolean]()
      val queuedEventPromise = Promise[Boolean]()
      val publishedEventPromise = Promise[Boolean]()
      val refreshedResourceStatePromise = Promise[Boolean]()
      val afterResubscriptionEventPromise = Promise[Boolean]()

      val clientMessageQueue: mutable.Queue[TextFrame] = mutable.Queue()
      val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
          clientMessageQueue += frame
          clientMessageQueue.size match {
            case 1 ⇒ resourceStatePromise.complete(Success(true))
            case 2 ⇒ queuedEventPromise.complete(Success(true))
            case 3 ⇒ publishedEventPromise.complete(Success(true))
            case 4 ⇒ refreshedResourceStatePromise.complete(Success(true))
            case 5 ⇒ afterResubscriptionEventPromise.complete(Success(true))
          }
        }

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "reliable-feed-client")

      client ! Connect() // init websocket connection

      val initialResourceState = Ok(
        DynamicBody(Obj(Map("content" → Text("fullResource")))),
        Headers.plain(Map("revision" → Seq("1"),
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))

      val updatedResourceState = Ok(
        DynamicBody(Obj(Map("content" → Text("fullResource")))),
        Headers.plain(Map("revision" → Seq("4"),
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))

      val subscriptionRequest = FacadeRequest(Uri("/test-service/reliable"), "subscribe",
        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)

      val eventRev2 = ReliableFeedTestRequest(
        FeedTestBody("haha"),
        Headers.plain(Map("revision" → Seq("2"),
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))

      val eventRev3 = ReliableFeedTestRequest(
        FeedTestBody("haha"),
        Headers.plain(Map("revision" → Seq("3"),
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))

      val eventBadRev5 = ReliableFeedTestRequest(
        FeedTestBody("updateFromFuture"),
        Headers.plain(Map("revision" → Seq("5"),
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))

      val eventGoodRev5 = ReliableFeedTestRequest(
        FeedTestBody("haha"),
        Headers.plain(Map("revision" → Seq("5"),
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))

      testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
        initialResourceState,
        // emulate latency between request for full resource state and response
        _ ⇒ Thread.sleep(10000)) onSuccess {
        case subscription: Subscription ⇒ register(subscription)
      }

      whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        client ! subscriptionRequest
        Thread.sleep(3000)
        testService.publish(eventRev2)
      }

      whenReady(resourceStatePromise.future, Timeout(Span(15, Seconds))) { b ⇒
        val resourceStateMessage = clientMessageQueue.get(0)
        if (resourceStateMessage.isDefined) {
          val resourceState = resourceStateMessage.get.payload.utf8String
          val referenceState = """{"status":200,"headers":{"hyperBusRevision":["1"],"hyperBusMessageId":["messageId"],"hyperBusCorrelationId":["correlationId"]},"body":{"content":"fullResource"}}"""
          resourceState shouldBe referenceState
        } else fail("Full resource state wasn't sent to the client")
      }

      whenReady(queuedEventPromise.future, Timeout(Span(15, Seconds))) { b ⇒
        val queuedEventMessage = clientMessageQueue.get(1)
        if (queuedEventMessage.isDefined) {
          val receivedEvent = FacadeRequest(queuedEventMessage.get)
          val queuedEvent = DynamicRequest(Uri("/test-service/reliable"),
            DynamicBody(Obj(Map("content" → Text("haha")))),
            Headers.plain(Map(Header.METHOD → Seq(Method.FEED_POST),
              FacadeHeaders.CLIENT_REVISION → Seq("2"),
              Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
              FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
              FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId"))))
          receivedEvent.toString shouldBe queuedEvent.toString
        } else fail("Queued event wasn't sent to the client")

        testService.publish(eventRev3)
      }

      whenReady(publishedEventPromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val directEventMessage = clientMessageQueue.get(2)
        if (directEventMessage.isDefined) {
          val receivedEvent = FacadeRequest(directEventMessage.get)
          val directEvent = DynamicRequest(Uri("/test-service/reliable"),
            DynamicBody(Obj(Map("content" → Text("haha")))),
            Headers.plain(Map(Header.METHOD → Seq(Method.FEED_POST),
              FacadeHeaders.CLIENT_REVISION → Seq("3"),
              Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
              FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
              FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId"))))
          receivedEvent.toString shouldBe directEvent.toString
        } else fail("Last event wasn't sent to the client")

        subscriptions.foreach(hyperBus.off)
        subscriptions.clear
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
          updatedResourceState) onSuccess {
          case subscription: Subscription ⇒ register(subscription)
        }
        // This event should be ignored, because it's an "event from future". Resource state retrieving should be triggered
        testService.publish(eventBadRev5)
      }

      whenReady(refreshedResourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val resourceUpdatedStateMessage = clientMessageQueue.get(3)
        if (resourceUpdatedStateMessage.isDefined) {
          val resourceUpdatedState = resourceUpdatedStateMessage.get.payload.utf8String
          val referenceState = """{"status":200,"headers":{"hyperBusMessageId":["messageId"],"hyperBusCorrelationId":["correlationId"],"hyperBusRevision":["4"]},"body":{"content":"fullResource"}}"""
          resourceUpdatedState shouldBe referenceState
        } else fail("Full resource state wasn't sent to the client")

        subscriptions.foreach(hyperBus.off)
        subscriptions.clear
        testService.publish(eventGoodRev5)
      }

      whenReady(afterResubscriptionEventPromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val directEventMessage = clientMessageQueue.get(4)
        if (directEventMessage.isDefined) {
          val receivedEvent = FacadeRequest(directEventMessage.get)
          val directEvent = DynamicRequest(Uri("/test-service/reliable"),
            DynamicBody(Obj(Map("content" → Text("haha")))),
            Headers.plain(Map(Header.METHOD → Seq(Method.FEED_POST),
              FacadeHeaders.CLIENT_REVISION → Seq("5"),
              Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
              FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
              FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId"))))
          receivedEvent.toString shouldBe directEvent.toString
        } else fail("Last event wasn't sent to the client")

        client ! FacadeRequest(Uri("/test-service/reliable"), "unsubscribe",
          Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
            FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)
      }
    }

    "websocket: get request" in {
      val host = "localhost"
      val port = 54321
      val url = "/status/test-service"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))

      val onClientUpgradePromise = Promise[Boolean]()
      val resourceStatePromise = Promise[Boolean]()

      var clientMessageQueue: mutable.Queue[TextFrame] = mutable.Queue()
      val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
          clientMessageQueue += frame
          clientMessageQueue.size match {
            case 1 ⇒ resourceStatePromise.complete(Success(true))
            case 2 ⇒ fail("there should not be incoming events after response")
          }
        }

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "get-client")

      client ! Connect() // init websocket connection

      testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
        Ok(
          DynamicBody(Obj(Map("content" → Text("fullResource")))),
          Headers.plain(Map(
            Header.REVISION → Seq("1"),
            Header.MESSAGE_ID → Seq("messageId"),
            Header.CORRELATION_ID → Seq("correlationId"))))) onSuccess {
        case subscr ⇒ register(subscr)
      }

      whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        client ! FacadeRequest(Uri("/test-service/reliable"), Method.GET,
          Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
            FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
            FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)
      }

      whenReady(resourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val resourceStateMessage = clientMessageQueue.get(0)
        if (resourceStateMessage.isDefined) {
          val resourceState = resourceStateMessage.get.payload.utf8String
          resourceState shouldBe """{"status":200,"headers":{"hyperBusRevision":["1"],"hyperBusMessageId":["messageId"],"hyperBusCorrelationId":["correlationId"]},"body":{"content":"fullResource"}}"""
        } else fail("Full resource state wasn't sent to the client")
      }
    }

    "websocket: post request" in {
      val host = "localhost"
      val port = 54321
      val url = "/status/test-service"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))

      val onClientUpgradePromise = Promise[Boolean]()
      val resourceStatePromise = Promise[Boolean]()

      var clientMessageQueue: mutable.Queue[TextFrame] = mutable.Queue()
      val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
          clientMessageQueue += frame
          clientMessageQueue.size match {
            case 1 ⇒ resourceStatePromise.complete(Success(true))
            case 2 ⇒ fail("there should not be incoming events after response")
          }
        }

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "post-client")

      client ! Connect() // init websocket connection

      testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.POST))),
        Ok(
          DynamicBody(Text("got it")),
          Headers.plain(Map(Header.MESSAGE_ID → Seq("messageId"), Header.CORRELATION_ID → Seq("correlationId"))))) onSuccess {
        case subscr ⇒ register(subscr)
      }

      whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        client ! FacadeRequest(Uri("/test-service/reliable"), Method.POST,
            Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
              FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
              FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
          Obj(Map("post request" → Text("some request body"))))
      }

      whenReady(resourceStatePromise.future, Timeout(Span(5, Seconds))) { b ⇒
        val resourceStateMessage = clientMessageQueue.get(0)
        if (resourceStateMessage.isDefined) {
          val resourceState = resourceStateMessage.get.payload.utf8String
          resourceState shouldBe """{"status":200,"headers":{"hyperBusMessageId":["messageId"],"hyperBusCorrelationId":["correlationId"]},"body":"got it"}"""
        } else fail("Full resource state wasn't sent to the client")
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

  def testServiceHyperBus: HyperBus = {
    val config = inject[Config]
    val testServiceTransportMgr = new TransportManager(TransportConfigurationLoader.fromConfig(config))
    val hypeBusGroupKey = "hyperbus.facade.group-name"
    val defaultHyperBusGroup = if (config.hasPath(hypeBusGroupKey)) Some(config.getString(hypeBusGroupKey)) else None
    new HyperBus(testServiceTransportMgr, defaultHyperBusGroup)(ExecutionContext.fromExecutor(newPoolExecutor()))
  }

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  override def afterEach(): Unit = {
    subscriptions.foreach(hyperBus.off)
    subscriptions.clear
  }

  def register(subscription: Subscription) = {
    subscriptions += subscription
  }
}
