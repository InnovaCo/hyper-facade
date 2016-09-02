package eu.inn.facade.filter.http

import eu.inn.auth.BasicAuthenticationService
import eu.inn.authentication.AuthUser
import eu.inn.binders.value.{Null, Text}
import eu.inn.facade.MockContext
import eu.inn.facade.model._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.Method
import eu.inn.facade.workers.WsRestServiceApp
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable
import spray.http.BasicHttpCredentials

import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticationRequestFilterTest extends FreeSpec with Matchers with ScalaFutures with Injectable with MockContext with BeforeAndAfterAll {

  implicit val injector = Injectors()
  inject[BasicAuthenticationService]
  val app = inject[Service].asInstanceOf[WsRestServiceApp]
  val filter = new AuthenticationRequestFilter

  override def afterAll(): Unit = {
    app.stopService(true)
  }

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
