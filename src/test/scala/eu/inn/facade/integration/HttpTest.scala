package eu.inn.facade.integration

import akka.actor.ActorSystem
import eu.inn.binders.value.{Null, Obj, ObjV, Text}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.client.pipelining._
import spray.http
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.{RawHeader, `Content-Type`}
import spray.http._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.io.Source

/**
  * Important: Kafka should be up and running to pass this test
  */
class HttpTest extends IntegrationTestBase("raml-configs/integration/http.raml") {

  "Integration. HTTP" - {
    "get. Add client ip to request" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/resource/with-client-ip")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(ObjV("a" → "response"))), { request ⇒
            request.uri shouldBe Uri("/resource/with-client-ip")
            request.body shouldBe DynamicBody(ObjV("emptyParam" → Null, "param" → "1", "clientIp" → "127.0.0.1"))
          }
        ).futureValue
      }

      Source.fromURL("http://localhost:54321/v3/resource/with-client-ip?param=1&emptyParam=", "UTF-8").mkString shouldBe """{"a":"response"}"""
    }

    "get. Resource is not configured in RAML" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/not-configured-resource")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response"))), { request ⇒
            request.uri shouldBe Uri("/not-configured-resource")
            request.body shouldBe DynamicBody(Obj(Map("emptyParam" → Null, "param" → Text("1"))))
          }
        ).futureValue
      }
      Source.fromURL("http://localhost:54321/v3/not-configured-resource?param=1&emptyParam=", "UTF-8").mkString shouldBe """"response""""
    }

    "get. Error responses: 404, 503" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/503-resource")), Map(Header.METHOD → Specific(Method.GET))),
          ServiceUnavailable(ErrorBody("service-is-not-available", Some("No connection to DB")))
        ).futureValue
      }

      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val uri404 = "http://localhost:54321/v3/404-resource"
      val response404 = pipeline(Get(http.Uri(uri404))).futureValue
      response404.entity.asString should include (""""code":"not-found"""")
      response404.status.intValue shouldBe 404

      val uri503 = "http://localhost:54321/v3/503-resource"
      val response503 = pipeline(Get(http.Uri(uri503))).futureValue
      response503.entity.asString should include (""""code":"service-is-not-available"""")
      response503.status.intValue shouldBe 503

      Await.result(clientActorSystem.terminate(), Duration.Inf)
    }

    "get. Retrieve state of resource with reliable feed support. Check Hyperbus-Revision and content-type" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/resource-with-reliable-feed")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response")), Headers(Map(Header.REVISION → Seq("1"), Header.CONTENT_TYPE → Seq("user-profile"))))
        ).futureValue
      }

      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val uri = "http://localhost:54321/v3/resource-with-reliable-feed"
      val response = pipeline(Get(http.Uri(uri))).futureValue

      response.entity.asString shouldBe """"response""""
      response.headers should contain (RawHeader("Hyperbus-Revision", "1"))

      val mediaType = MediaTypes.register(MediaType.custom("application", "vnd.user-profile+json", true, false))
      val contentType = ContentType(mediaType, `UTF-8`)
      response.headers should contain (`Content-Type`(contentType))

      Await.result(clientActorSystem.terminate(), Duration.Inf)
    }

    "get. Rewrite" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/rewritten-resource")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response"))), { request ⇒
            request.uri shouldBe Uri("/rewritten-resource")
          }
        ).futureValue
      }

      Source.fromURL("http://localhost:54321/v3/original-resource", "UTF-8").mkString shouldBe """"response""""
    }

    "get. Rewrite with arguments" in {
      register {
        testService.onCommand(RequestMatcher(Some(Uri("/rewritten-resource/{serviceId}")), Map(Header.METHOD → Specific(Method.GET))),
          Ok(DynamicBody(Text("response-with-args"))), { request ⇒
            request.uri shouldBe Uri("/rewritten-resource/{serviceId}", Map("serviceId" → "100501"))
          }
        ).futureValue
      }

      Source.fromURL("http://localhost:54321/v3/original-resource/100501", "UTF-8").mkString shouldBe """"response-with-args""""
    }

    "get. Deny response filter" in {
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

      val str = Source.fromURL("http://localhost:54321/v3/users/100500", "UTF-8").mkString
      str should include (""""userName":"jsmith"""")
      str shouldNot include ("""password""")
    }

    "get. Deny request filter" in {
      val clientActorSystem = ActorSystem()
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))

      val uri403 = "http://localhost:54321/v3/403-resource"
      val response403 = pipeline(Get(http.Uri(uri403))).futureValue
      response403.entity.asString should include (""""code":"forbidden"""")
      response403.status.intValue shouldBe 403

      Await.result(clientActorSystem.terminate(), Duration.Inf)
    }
  }
}
