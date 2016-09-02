package eu.inn.facade.filter.raml

import eu.inn.authentication.AuthUser
import eu.inn.binders.value.{Null, Text}
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.model.ContextStorage.ExtendFacadeRequestContext
import eu.inn.facade.model._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.workers.WsRestServiceApp
import eu.inn.facade.{FacadeConfigPaths, TestBase}
import eu.inn.hyperbus.model.Method
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service

import scala.concurrent.ExecutionContext.Implicits.global

class AuthorizeRequestFilterTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/auth-request-filter-test.raml")
  implicit val injector = Injectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")
  val app = inject[Service].asInstanceOf[WsRestServiceApp]

  "AuthorizeRequestFilterTest" - {
    "resource. not authorized" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(unauthorizedRequest), unauthorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe false
    }

    "resource. authorized" in {
      val authorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(authorizedRequest)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.AUTH_USER → AuthUser("123456", Set.empty, Null))
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), authorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe true
    }

    "method. not authorized" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.POST,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(unauthorizedRequest), unauthorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe false
    }

    "method. authorized" in {
      val authorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.POST,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(authorizedRequest)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.AUTH_USER → AuthUser("123456", Set.empty, Null))
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), authorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe true
    }
  }
}
