package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Null, Obj, ObjV, Text}
import eu.inn.facade.{TestBase, TestService}
import eu.inn.facade.model.{ContextWithRequest, FacadeRequest, FacadeResponse}
import eu.inn.facade.modules.TestInjectors
import eu.inn.facade.raml.Method
import eu.inn.facade.workers.TestWsRestServiceApp
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global

class EmbedResponseFilterTest extends TestBase {
  implicit val injector = TestInjectors()
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]
  val filter = new EmbedResponseFilter("banner")
  val testService = inject[TestService]

  "EmbedResponseFilter" - {
    "embed single relation" in {
      val response = FacadeResponse(200, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_links" → ObjV(
            "banner" → ObjV("href" → "/revelations/templates/{templateId}", "templated" → true)
          ),
          "templateId" → 1
        )
      )

      val bannerResponse = Ok(DynamicBody(Obj(Map("bannerContent" → Text("Fill the form")))))
      testService.onCommand(RequestMatcher(Some(Uri("/revelations/templates/{templateId}", Map("templateId" → "1"))), Map(Header.METHOD → Specific(Method.GET))),
        bannerResponse
      )
      val request = FacadeRequest(Uri("/revelations/{userId}", Map("userId" → "2")), Method.GET, Map("messageId" → Seq("subRequest")), Null)

      val filteredResponse = filter.apply(ContextWithRequest(mockContext(request), request), response).futureValue(Timeout(Span(10, Seconds)))
      filteredResponse.body shouldBe ObjV("bannerContent" → Text("Fill the form"))
    }
  }
}
