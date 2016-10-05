package eu.inn.facade.filter.raml

import eu.inn.binders.value.{Lst, Null, Obj, ObjV, Text}
import eu.inn.facade.model.{ContextWithRequest, FacadeRequest}
import eu.inn.facade.modules.TestInjectors
import eu.inn.facade.workers.TestWsRestServiceApp
import eu.inn.facade.{TestBase, TestService}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global

class EmbedEventFilterTest extends TestBase {
  implicit val injector = TestInjectors()
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]
  val filter = new EmbedEventFilter("banner")
  val testService = inject[TestService]

  "EmbedEventFilter" - {
    "embed single relation" in {
      val event = FacadeRequest(Uri("/revelations"), Method.FEED_PUT, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_links" → ObjV(
            "banner" → ObjV("href" → Text("/revelations/templates/{templateId}"), "templated" → true)
          ),
          "templateId" → 1
        )
      )

      val bannerResponse = Ok(DynamicBody(Obj(Map("bannerContent" → Text("Fill the form")))))
      testService.onCommand(RequestMatcher(Some(Uri("/revelations/templates/{templateId}", Map("templateId" → "1"))), Map(Header.METHOD → Specific(Method.GET))),
        bannerResponse
      )
      val request = FacadeRequest(Uri("/revelations/{userId}", Map("userId" → "2")), Method.GET, Map("messageId" → Seq("subRequest")), Null)

      val filteredEvent = filter.apply(ContextWithRequest(mockContext(request), request), event).futureValue(Timeout(Span(10, Seconds)))
      filteredEvent.body.asInstanceOf[Obj].v("bannerContent") shouldBe Text("Fill the form")
    }

    "embed Lst relation" in {
      val event = FacadeRequest(Uri("/revelations"), Method.FEED_PUT, Map("messageId" → Seq("#12345"), "correlationId" → Seq("#54321")),
        ObjV(
          "_links" → ObjV(
            "banner" → Lst(Seq(
              ObjV("href" → Text("/revelations/templates/{templateId}"), "templated" → true),
              ObjV("href" → Text("/sub-revelations/templates/{templateId}"), "templated" → true)))
          ),
          "templateId" → 1
        )
      )

      val bannerResponse = Ok(DynamicBody(Obj(Map("bannerContent" → Text("Fill the form")))))
      val subBannerResponse = Ok(DynamicBody(Obj(Map("subBannerContent" → Text("Fill one more form")))))
      testService.onCommand(RequestMatcher(Some(Uri("/revelations/templates/{templateId}", Map("templateId" → "1"))), Map(Header.METHOD → Specific(Method.GET))),
        bannerResponse, request ⇒ println(request)
      )
      testService.onCommand(RequestMatcher(Some(Uri("/sub-revelations/templates/{templateId}", Map("templateId" → "1"))), Map(Header.METHOD → Specific(Method.GET))),
        subBannerResponse, request ⇒ println(request)
      )
      val request = FacadeRequest(Uri("/revelations/{userId}", Map("userId" → "2")), Method.GET, Map("messageId" → Seq("subRequest")), Null)

      val filteredEvent = filter.apply(ContextWithRequest(mockContext(request), request), event).futureValue(Timeout(Span(300, Seconds)))
      filteredEvent.body.asInstanceOf[Obj].v("bannerContent") shouldBe Text("Fill the form")
      filteredEvent.body.asInstanceOf[Obj].v("subBannerContent") shouldBe Text("Fill one more form")
    }
  }
}
