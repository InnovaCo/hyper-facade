package eu.inn.facade.integration

import eu.inn.binders.json._
import eu.inn.binders.value.{Null, Obj, ObjV, Text}
import eu.inn.facade._
import eu.inn.facade.workers.TestQueue
import eu.inn.facade.model._
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri

/**
  * Important: Kafka should be up and running to pass this test
  */
class WebsocketTest extends IntegrationTestBase("raml-configs/integration/websocket.raml") {

  "Integration. Websockets" - {

    "unreliable feed" in {
      val q = new TestQueue
      val client = createWsClient("unreliable-feed-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/resource/unreliable-feed")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Obj(Map("content" → Text("fullResource")))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/v3/resource/unreliable-feed"), "subscribe",
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

      q.next().futureValue shouldBe """{"uri":"/v3/resource/unreliable-feed","method":"feed:post","headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"],"Content-Type":["application/vnd.feed-test+json"]},"body":{"content":"haha"}}"""

      client ! FacadeRequest(Uri("/v3/resource-with-unreliable-feed"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)
    }

    "handle error response" in {
      val q = new TestQueue
      val client = createWsClient("error-feed-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/500-resource")), Map(Header.METHOD → Specific(Method.GET))),
          eu.inn.hyperbus.model.InternalServerError(ErrorBody("unhandled-exception", Some("Internal server error"), errorId = "123"))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/v3/500-resource"), "subscribe",
          Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
            FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
            FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
          Obj(Map("content" → Text("haha"))))

      val resourceState = q.next().futureValue
      resourceState should startWith ("""{"status":500,"headers":""")
      resourceState should include (""""code":"unhandled-exception"""")
    }

    "reliable feed" in {
      val q = new TestQueue
      val client = createWsClient("reliable-feed-client", "/v3/ugprade", q.put)

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

      val subscriptionRequest = FacadeRequest(Uri("/v3/resource/reliable-feed"), "subscribe",
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
        testService.onCommand(RequestMatcher(Some(Uri("/resource/reliable-feed")), Map(Header.METHOD → Specific(Method.GET))),
          initialResourceState,
          // emulate latency between request for full resource state and response
          _ ⇒ Thread.sleep(7000)
        ).futureValue
      }

      client ! subscriptionRequest
      Thread.sleep(3000)
      testService.publish(eventRev2)
      testService.publish(eventRev3)

      val resourceState = q.next().futureValue
      resourceState shouldBe """{"status":200,"headers":{"Hyperbus-Revision":["1"],"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":{"content":"fullResource"}}"""

      val receivedEvent1 = q.next().futureValue
      val queuedEvent = FacadeRequest(Uri("/v3/resource/reliable-feed"), Method.FEED_POST,
        Map(FacadeHeaders.CLIENT_REVISION → Seq("2"),
          FacadeHeaders.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        ObjV("content" → Text("haha"))
      )
      receivedEvent1 shouldBe queuedEvent.toJson

      val receivedEvent2 = q.next().futureValue
      val directEvent2 = FacadeRequest(Uri("/v3/resource/reliable-feed"), Method.FEED_POST,
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
        testService.onCommand(RequestMatcher(Some(Uri("/resource/reliable-feed")), Map(Header.METHOD → Specific(Method.GET))),
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
      val directEvent3 = FacadeRequest(Uri("/v3/resource/reliable-feed"), Method.FEED_POST,
        Map(FacadeHeaders.CLIENT_REVISION → Seq("5"),
          FacadeHeaders.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        ObjV("content" → Text("haha"))
      )
      receivedEvent3 shouldBe directEvent3.toJson

      client ! FacadeRequest(Uri("/v3/resource/reliable-feed"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null
      )
    }

    "unreliable events feed with rewrite" in {
      val q = new TestQueue
      val client = createWsClient("unreliable-rewrite-feed-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/rewritten-resource/{serviceId}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Obj(Map("content" → Text("fullResource-154")))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/v3/original-resource/100500"), "subscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        Null
      )

      val resourceState = q.next().futureValue
      resourceState should startWith("""{"status":200,"headers":{"Hyperbus-Message-Id":""")
      resourceState should endWith("""body":{"content":"fullResource-154"}}""")

      testService.publish(UnreliableRewriteFeedTestRequest(
        "100500",
        FeedTestBody("haha"),
        Headers.plain(Map(
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))
      )

      q.next().futureValue shouldBe """{"uri":"/v3/original-resource/100500","method":"feed:put","headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"],"Content-Type":["application/vnd.feed-test+json"]},"body":{"content":"haha"}}"""

      client ! FacadeRequest(Uri("/v3/original-resource/100500"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)
    }

    "unreliable events feed with rewrite (not matching of request context)" in {
      val q = new TestQueue
      val client = createWsClient("unreliable-rewrite-nm-feed-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/rewritten-events/{path:*}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Obj(Map("content" → Text("rewritten-events-not-matching-context")))))
        ).futureValue
      }

      // hacky {path:*} specific event handling
      client ! FacadeRequest(Uri("/v3/events"), "subscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")),
        Null
      )

      val resourceState = q.next().futureValue
      resourceState should startWith("""{"status":200,"headers":{"Hyperbus-Message-Id":""")
      resourceState should endWith("""body":{"content":"rewritten-events-not-matching-context"}}""")

      testService.publish(RewriteOutsideFeedTestRequest(
        "root/1",
        DynamicBody(ObjV("content" → "event1")),
        Headers.plain(Map(
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))
      )

      q.next().futureValue shouldBe """{"uri":"/v3/events/1","method":"feed:put","headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["messageId"]},"body":{"content":"event1"}}"""

      testService.publish(RewriteOutsideFeedTestRequest(
        "root/2",
        DynamicBody(ObjV("content" → "event2")),
        Headers.plain(Map(
          Header.MESSAGE_ID → Seq("messageId"),
          Header.CORRELATION_ID → Seq("correlationId"))))
      )

      q.next().futureValue shouldBe """{"uri":"/v3/events/2","method":"feed:put","headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["messageId"]},"body":{"content":"event2"}}"""

      client ! FacadeRequest(Uri("/v3/events"), "unsubscribe",
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")), Null)
    }

    "get" in {
      val q = new TestQueue
      val client = createWsClient("get-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/resource")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(
            DynamicBody(Obj(Map("content" → Text("fullResource")))),
            Headers.plain(Map(
              Header.REVISION → Seq("1"),
              Header.MESSAGE_ID → Seq("messageId"),
              Header.CORRELATION_ID → Seq("correlationId"))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/v3/resource"), Method.GET,
        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Null)

      val resourceState = q.next().futureValue
      resourceState shouldBe """{"status":200,"headers":{"Hyperbus-Revision":["1"],"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":{"content":"fullResource"}}"""
    }

    "post" in {
      val q = new TestQueue
      val client = createWsClient("post-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/resource")), Map(Header.METHOD → Specific(Method.POST))),
          Ok(
            DynamicBody(Text("got it")),
            Headers.plain(Map(Header.MESSAGE_ID → Seq("messageId"), Header.CORRELATION_ID → Seq("correlationId"))))
        ).futureValue
      }

      client ! FacadeRequest(Uri("/v3/resource"), Method.POST,
        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
        Obj(Map("post request" → Text("some request body"))))

      val resourceState = q.next().futureValue
      resourceState shouldBe """{"status":200,"headers":{"Hyperbus-Message-Id":["messageId"],"Hyperbus-Correlation-Id":["correlationId"]},"body":"got it"}"""
    }

    "get. Rewrite with arguments" in {
      val q = new TestQueue
      val client = createWsClient("ws-get-rewrite-args-client", "/v3/upgrade", q.put)

      register {
        testService.onCommand(RequestMatcher(Some(Uri("/rewritten-resource/{serviceId}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response"))), { request ⇒
            request.uri shouldBe Uri("/rewritten-resource/{serviceId}", Map("serviceId" → "100500"))
          }
        ).futureValue
      }

      client ! FacadeRequest(Uri("/v3/original-resource/100500"), Method.GET,
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")), Null)

      val resourceState = q.next().futureValue
      resourceState should include (""""response"""")
    }

    "get. Deny response filter" in {
      val q = new TestQueue
      val client = createWsClient("ws-get-private-client", "/v3/upgrade", q.put)

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

      client ! FacadeRequest(Uri("/v3/users/100500"), Method.GET,
        Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId")), Null)

      val resourceState = q.next().futureValue
      resourceState should include (""""userName":"jsmith"""")
      resourceState shouldNot include ("""password""")
    }

    "get. Deny event filter" in {
      val q = new TestQueue
      val client = createWsClient("ws-get-private-event-client", "/v3/upgrade", q.put)

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

      client ! FacadeRequest(Uri("/v3/users/100500"), "subscribe",
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
  }
}
