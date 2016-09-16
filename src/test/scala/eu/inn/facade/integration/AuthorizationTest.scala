package eu.inn.facade.integration

import akka.actor.ActorSystem
import eu.inn.binders.value.ObjV
import eu.inn.hyperbus.model.{DynamicBody, Header, Method, Ok}
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri
import spray.client.pipelining._
import spray.http
import spray.http.HttpHeaders.Authorization
import spray.http.{BasicHttpCredentials, HttpRequest, HttpResponse}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class AuthorizationTest { //extends IntegrationTestBase("raml-configs/integration/authorization.raml") {

//  "Authorization and authentication integration test" - {
//    "http. wrong credentials. 401 unauthorized" in {
//      val clientActorSystem = ActorSystem()
//      val authHeader = Authorization(BasicHttpCredentials("wrongUser", "wrongPassword"))
//      val pipeline: HttpRequest => Future[HttpResponse] = addHeader(authHeader) ~> sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
//      val response = pipeline(Get(http.Uri("http://localhost:54321/v3/authentication-failed"))).futureValue
//      response.entity.asString should include (""""code":"unauthorized"""")
//      response.status.intValue shouldBe 401
//
//      Await.result(clientActorSystem.terminate(), Duration.Inf)
//    }
//
//    "http. wrong role. 403 forbidden" in {
//      val clientActorSystem = ActorSystem()
//      val authHeader = Authorization(BasicHttpCredentials("admin", "admin"))
//      val pipeline: HttpRequest => Future[HttpResponse] = addHeader(authHeader) ~> sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
//      val response = pipeline(Get(http.Uri("http://localhost:54321/v3/authorization-failed"))).futureValue
//      response.entity.asString should include (""""code":"forbidden"""")
//      response.status.intValue shouldBe 403
//
//      Await.result(clientActorSystem.terminate(), Duration.Inf)
//    }


    // todo: uncomment when fixed https://github.com/InnovaCo/hyperbus/issues/12

//    "http. 200 ok" in {
//      register {
//        testService.onCommand(RequestMatcher(Some(Uri("/resource")), Map(Header.METHOD → Specific(Method.GET))),
//          Ok(DynamicBody(ObjV("a" → "response"))), { request ⇒
//            request.uri shouldBe Uri("/resource")
//          }
//        ).futureValue
//      }
//
//      val clientActorSystem = ActorSystem()
//      val authHeader = Authorization(BasicHttpCredentials("admin", "admin"))
//      val pipeline: HttpRequest => Future[HttpResponse] = addHeader(authHeader) ~> sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
//      val response = pipeline(Get(http.Uri("http://localhost:54321/v3/resource"))).futureValue
//      response.entity.asString  shouldBe """{"a":"response"}"""
//      response.status.intValue shouldBe 200
//
//      Await.result(clientActorSystem.terminate(), Duration.Inf)
//    }
//  }
}
