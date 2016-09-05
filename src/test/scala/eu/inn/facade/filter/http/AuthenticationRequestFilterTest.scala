package eu.inn.facade.filter.http

import eu.inn.auth.BasicAuthenticationService
import eu.inn.authentication.AuthUser
import eu.inn.binders.value.{Null, Text}
import eu.inn.facade.TestBase
import eu.inn.facade.model._
import eu.inn.facade.modules.TestInjectors
import eu.inn.facade.raml.Method
import eu.inn.facade.workers.TestWsRestServiceApp
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service
import spray.http.BasicHttpCredentials

import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticationRequestFilterTest extends TestBase {

  implicit val injector = TestInjectors()
  inject[BasicAuthenticationService]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]
  val filter = new AuthenticationRequestFilter

  "AuthenticationFilter" - {
    "unauthorized: non-existent user" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map(FacadeHeaders.AUTHORIZATION → Seq(BasicHttpCredentials("login", "password").toString())),
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(unauthorizedRequest)

      val fail = filter.apply(ContextWithRequest(requestContext, unauthorizedRequest)).failed.futureValue
      fail shouldBe a [FilterInterruptException]

      val response = fail.asInstanceOf[FilterInterruptException].response
      response.status shouldBe 401
    }

    "unauthorized: wrong password" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map(FacadeHeaders.AUTHORIZATION → Seq(BasicHttpCredentials("admin", "wrong-password").toString())),
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(unauthorizedRequest)

      val fail = filter.apply(ContextWithRequest(requestContext, unauthorizedRequest)).failed.futureValue
      fail shouldBe a [FilterInterruptException]

      val response = fail.asInstanceOf[FilterInterruptException].response
      response.status shouldBe 401
    }

    "successful" in {
      val request = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map(FacadeHeaders.AUTHORIZATION → Seq(BasicHttpCredentials("admin", "admin").toString())),
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(request)

      val filteredRequestContext = filter.apply(ContextWithRequest(requestContext, request)).futureValue.context
      val authUser = filteredRequestContext.contextStorage(ContextStorage.AUTH_USER).asInstanceOf[AuthUser]
      authUser.id shouldBe "1"
      authUser.roles should contain("admin")
      authUser.properties shouldBe Null
    }
  }
}
