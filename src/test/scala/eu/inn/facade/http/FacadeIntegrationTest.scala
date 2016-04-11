package eu.inn.facade.http

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import com.typesafe.config.Config
import eu.inn.binders.json._
import eu.inn.binders.value.{Null, Obj, ObjV, Text}
import eu.inn.facade.model.{FacadeHeaders, FacadeRequest, UriSpecificDeserializer, UriSpecificSerializer}
import eu.inn.facade.modules.Injectors
import eu.inn.facade.{FeedTestBody, ReliableFeedTestRequest, TestService, UnreliableFeedTestRequest}
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.api.{Subscription, TransportConfigurationLoader, TransportManager}
import eu.inn.servicecontrol.api.Service
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FreeSpec, Matchers}
import scaldi.Injectable
import spray.client.pipelining._
import spray.http
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.{RawHeader, `Content-Type`}
import spray.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

/**
  * Important: Kafka should be up and running to pass this test
  */
class FacadeIntegrationTest extends FreeSpec with Matchers with ScalaFutures with Injectable
  with BeforeAndAfterEach with WsTestClientHelper with Eventually {
  implicit val injector = Injectors()
  implicit val actorSystem = inject[ActorSystem]
  implicit val patience = PatienceConfig(scaled(Span(15, Seconds)))
  implicit val timeout = akka.util.Timeout(15.seconds)
  implicit val uid = new UriSpecificDeserializer
  implicit val uis = new UriSpecificSerializer

  val httpWorker = inject[HttpWorker]

  inject[Service].asInstanceOf[WsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }
  val hyperbus = inject[Hyperbus] // initialize hyperbus
  val testService = new TestService(testServiceHyperbus)
  val subscriptions = scala.collection.mutable.MutableList[Subscription]()

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(1000)

  "Facade integration" - {
    "http get. Resource configured in RAML" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/status/test-service")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(ObjV("a" → "response"))), { request ⇒
            request.uri shouldBe Uri("/status/test-service")
            request.body shouldBe DynamicBody(ObjV("emptyParam" → Null, "param" → "1", "clientIp" → "127.0.0.1"))
          }
        ).futureValue
      }

      Source.fromURL("http://localhost:54321/status/test-service?param=1&emptyParam=", "UTF-8").mkString shouldBe """{"a":"response"}"""
    }

    "http get. Resource is not configured in RAML" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/someSecretResource")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response"))), { request ⇒
            request.uri shouldBe Uri("/someSecretResource")
            request.body shouldBe DynamicBody(Obj(Map("emptyParam" → Null, "param" → Text("1"))))
          }
        ).futureValue
      }
      Source.fromURL("http://localhost:54321/someSecretResource?param=1&emptyParam=", "UTF-8").mkString shouldBe """"response""""
    }

    "http get. Error response" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/failed-resource")), Map(Header.METHOD → Specific(Method.GET))),
          ServiceUnavailable(ErrorBody("service-is-not-available", Some("No connection to DB")))
        ).futureValue
      }

      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val uri404 = "http://localhost:54321/test-service/reliable"
      val response404 = pipeline(Get(http.Uri(uri404))).futureValue
      response404.entity.asString should include (""""code":"not-found"""")
      response404.status.intValue shouldBe 404

      val uri503 = "http://localhost:54321/failed-resource"
      val response503 = pipeline(Get(http.Uri(uri503))).futureValue
      response503.entity.asString should include (""""code":"service-is-not-available"""")
      response503.status.intValue shouldBe 503

      Await.result(clientActorSystem.terminate(), Duration.Inf)
    }

    "http get reliable resource. Check Hyperbus-Revision" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response")), Headers(Map(Header.REVISION → Seq("1"), Header.CONTENT_TYPE → Seq("user-profile"))))
        ).futureValue
      }

      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val uri = "http://localhost:54321/test-service/reliable"
      val response = pipeline(Get(http.Uri(uri))).futureValue

      response.entity.asString shouldBe """"response""""
      response.headers should contain (RawHeader("Hyperbus-Revision", "1"))

      val mediaType = MediaTypes.register(MediaType.custom("application", "vnd.user-profile+json", true, false))
      val contentType = ContentType(mediaType, `UTF-8`)
      response.headers should contain (`Content-Type`(contentType))

      Await.result(clientActorSystem.terminate(), Duration.Inf)
    }

    "websocket: unreliable feed" in {
      val q = new TestQueue
      val client = createWsClient("unreliable-feed-client", "/status/test-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/unreliable")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Obj(Map("content" → Text("fullResource")))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/test-service/unreliable"), "subscribe",
        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        Obj(Map("content" → Text("haha")))
      )

      val resourceState = q.next().futureValue
      resourceState should startWith("""{"status":200,"headers":{"Hyperbus-Message-Id":""")
      resourceState should endWith("""body":{"content":"fullResource"}}""")

      testService.publish(UnreliableFeedTestRequest(
          FeedTestBody("haha"),
          Headers.plain(Map(
            Header.MESSAGE_ID → Seq("messageId"),
            Header.CORRELATION_ID → Seq("correlationId"))))
      )

      q.next().futureValue shouldBe """{"uri":"/v3/test-service/unreliable","method":"feed:post","headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"],"Content-Type":["application/vnd.feed-test+json"]},"body":{"content":"haha"}}"""

      client ! FacadeRequest(Uri("/test-service/unreliable"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)
    }

    "websocket: handle error response" in {
      val q = new TestQueue
      val client = createWsClient("error-feed-client", "/status/test-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/unreliable")), Map(Header.METHOD → Specific(Method.GET))),
          eu.inn.hyperbus.model.InternalServerError(ErrorBody("unhandled-exception", Some("Internal server error"), errorId = "123"))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/test-service/unreliable"), "subscribe",
          Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
            FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
            FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
          Obj(Map("content" → Text("haha"))))

      val resourceState = q.next().futureValue
      resourceState should startWith ("""{"status":500,"headers":""")
      resourceState should include (""""code":"unhandled-exception"""")
    }

    "websocket: reliable feed" in {
      val q = new TestQueue
      val client = createWsClient("reliable-feed-client", "/status/test-service", q.put)

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
        Map(FacadeHeaders.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
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

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
          initialResourceState,
          // emulate latency between request for full resource state and response
          _ ⇒ Thread.sleep(10000)
        ).futureValue
      }

      client ! subscriptionRequest
      Thread.sleep(3000)
      testService.publish(eventRev2)

      val resourceState = q.next().futureValue
      resourceState shouldBe """{"status":200,"headers":{"Hyperbus-Revision":["1"],"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":{"content":"fullResource"}}"""

      val receivedEvent1 = q.next().futureValue
      val queuedEvent = FacadeRequest(Uri("/v3/test-service/reliable"), Method.FEED_POST,
        Map(FacadeHeaders.CLIENT_REVISION → Seq("2"),
          FacadeHeaders.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        ObjV("content" → Text("haha"))
      )
      receivedEvent1 shouldBe queuedEvent.toJson

      testService.publish(eventRev3)

      val receivedEvent2 = q.next().futureValue
      val directEvent2 = FacadeRequest(Uri("/v3/test-service/reliable"), Method.FEED_POST,
        Map(FacadeHeaders.CLIENT_REVISION → Seq("3"),
          FacadeHeaders.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        ObjV("content" → Text("haha"))
      )
      receivedEvent2 shouldBe directEvent2.toJson

      subscriptions.foreach(hyperbus.off)
      subscriptions.clear

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
          updatedResourceState
        ).futureValue
      }
      // This event should be ignored, because it's an "event from future". Resource state retrieving should be triggered
      testService.publish(eventBadRev5)

      val resourceUpdatedState = q.next().futureValue
      resourceUpdatedState shouldBe """{"status":200,"headers":{"Hyperbus-Revision":["4"],"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":{"content":"fullResource"}}"""

      subscriptions.foreach(hyperbus.off)
      subscriptions.clear
      testService.publish(eventGoodRev5)

      val receivedEvent3 = q.next().futureValue
      val directEvent3 = FacadeRequest(Uri("/v3/test-service/reliable"), Method.FEED_POST,
        Map(FacadeHeaders.CLIENT_REVISION → Seq("5"),
          FacadeHeaders.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        ObjV("content" → Text("haha"))
      )
      receivedEvent3 shouldBe directEvent3.toJson

      client ! FacadeRequest(Uri("/test-service/reliable"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null
      )
    }

    "websocket: get request" in {
      val q = new TestQueue
      val client = createWsClient("get-client", "/status/test-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(
            DynamicBody(Obj(Map("content" → Text("fullResource")))),
            Headers.plain(Map(
              Header.REVISION → Seq("1"),
              Header.MESSAGE_ID → Seq("messageId"),
              Header.CORRELATION_ID → Seq("correlationId"))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/test-service/reliable"), Method.GET,
        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)

      val resourceState = q.next().futureValue
      resourceState shouldBe """{"status":200,"headers":{"Hyperbus-Revision":["1"],"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":{"content":"fullResource"}}"""
    }

    "websocket: post request" in {
      val q = new TestQueue
      val client = createWsClient("post-client", "/status/test-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/test-service/reliable")), Map(Header.METHOD → Specific(Method.POST))),
          Ok(
            DynamicBody(Text("got it")),
            Headers.plain(Map(Header.MESSAGE_ID → Seq("messageId"), Header.CORRELATION_ID → Seq("correlationId"))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/test-service/reliable"), Method.POST,
        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        Obj(Map("post request" → Text("some request body"))))

      val resourceState = q.next().futureValue
      resourceState shouldBe """{"status":200,"headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":"got it"}"""
    }

    "websocket: get with rewrite" in {
      val q = new TestQueue
      val client = createWsClient("ws-get-rewrite-client", "/test-rewrite/some-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/status/test-service")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response"))), { request ⇒
            request.uri shouldBe Uri("/status/test-service")
          }
        ).futureValue
      }

      client ! FacadeRequest(Uri("/test-rewrite/some-service"), Method.GET,
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")), Null)

      val resourceState = q.next().futureValue
      resourceState should include (""""response"""")
    }

    "websocket: get applies private response filter" in {
      val q = new TestQueue
      val client = createWsClient("ws-get-private-client", "/test-rewrite/some-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/users/{userId}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(
            ObjV(
              "fullName" → "John Smith",
              "userName" → "jsmith",
              "password" → "abyrvalg"
            )
          )), { request ⇒
            request.uri shouldBe Uri("/users/{userId}", Map("userId" → "100500"))
          }
        ).futureValue
      }

      client ! FacadeRequest(Uri("/users/100500"), Method.GET,
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")), Null)

      val resourceState = q.next().futureValue
      resourceState should include (""""userName":"jsmith"""")
      resourceState shouldNot include ("""password""")
    }

    "websocket: get applies private event filter" in {
      val q = new TestQueue
      val client = createWsClient("ws-get-private-event-client", "/test-rewrite/some-service", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/users/{userId}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(
            ObjV(
              "fullName" → "John Smith",
              "userName" → "jsmith",
              "password" → "abyrvalg"
            )
          )), { request ⇒
            request.uri shouldBe Uri("/users/{userId}", Map("userId" → "100500"))
          }
        ).futureValue
      }

      client ! FacadeRequest(Uri("/users/100500"), "subscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")), Null)

      val resourceState = q.next().futureValue
      resourceState should include (""""userName":"jsmith"""")
      resourceState shouldNot include ("""password""")

      hyperbus <| DynamicRequest(Uri("/users/{userId}", Map("userId" → "100500")), "feed:put", DynamicBody(
        ObjV("fullName" → "John Smith", "userName" → "newsmith", "password" → "neverforget")
      ))

      val event = q.next().futureValue
      event should include (""""userName":"newsmith"""")
      event shouldNot include ("""password""")
    }

    "http get with rewrite" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/status/test-service")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response"))), { request ⇒
            request.uri shouldBe Uri("/status/test-service")
          }
        ).futureValue
      }

      Source.fromURL("http://localhost:54321/test-rewrite/some-service", "UTF-8").mkString shouldBe """"response""""
    }

    "http get applies private response filter" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/users/{userId}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(
            ObjV(
              "fullName" → "John Smith",
              "userName" → "jsmith",
              "password" → "abyrvalg"
            )
          )), { request ⇒
            request.uri shouldBe Uri("/users/{userId}", Map("userId" → "100500"))
          }
        ).futureValue
      }

      val str = Source.fromURL("http://localhost:54321/users/100500", "UTF-8").mkString
      str should include (""""userName":"jsmith"""")
      str shouldNot include ("""password""")
    }
  }

  def testServiceHyperbus: Hyperbus = {
    val config = inject[Config]
    val testServiceTransportMgr = new TransportManager(TransportConfigurationLoader.fromConfig(config))
    val hypeBusGroupKey = "hyperbus.facade.group-name"
    val defaultHyperbusGroup = if (config.hasPath(hypeBusGroupKey)) Some(config.getString(hypeBusGroupKey)) else None
    new Hyperbus(testServiceTransportMgr, defaultHyperbusGroup)(ExecutionContext.fromExecutor(newPoolExecutor()))
  }

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  override def afterEach(): Unit = {
    subscriptions.foreach(hyperbus.off)
    subscriptions.clear
  }

  def register(s: Subscription) = {
    subscriptions += s
  }
}
