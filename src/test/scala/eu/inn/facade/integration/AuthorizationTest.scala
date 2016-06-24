package eu.inn.facade.integration

import akka.actor.ActorSystem
import eu.inn.authentication.BasicAuthenticationService
import spray.client.pipelining._
import spray.http
import spray.http.{BasicHttpCredentials, HttpRequest, HttpResponse}
import spray.http.HttpHeaders.Authorization

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class AuthorizationTest extends IntegrationTestBase("specific-raml-configs/integration/authorization.raml") {

  inject[BasicAuthenticationService]

  "Authorization and authentication integration test" - {
    "http. wrong credentials. 401" in {
      val clientActorSystem = ActorSystem()
      val authHeader = Authorization(BasicHttpCredentials("wrongUser", "wrongPassword"))
      val pipeline: HttpRequest => Future[HttpResponse] = addHeader(authHeader) ~> sendReceive(clientActorSystem, ExecutionContext.fromExecutor(newPoolExecutor()))
      val response = pipeline(Get(http.Uri("http://localhost:54321/v3/authentication-failed"))).futureValue
      response.entity.asString should include (""""code":"unauthorized"""")
      response.status.intValue shouldBe 401

      Await.result(clientActorSystem.terminate(), Duration.Inf)
    }
  }
}
