package eu.inn.facade.raml

import eu.inn.facade.CleanRewriteIndex
import eu.inn.facade.filter.model.{ConditionalEventFilterWrapper, ConditionalRequestFilterWrapper, ConditionalResponseFilterWrapper}
import eu.inn.facade.filter.raml._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.Method._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

class RamlConfigParserTest extends FreeSpec with Matchers with CleanRewriteIndex with Injectable {
  implicit val injector = Injectors()
  val ramlConfig = inject[RamlConfig]

  "RamlConfig" - {
//    "traits" in {
//      ramlConfig.traitNames("/status", POST) shouldBe Seq("rateLimited")
//      ramlConfig.traitNames("/users/{userId}", GET) shouldBe Seq("secured", "rateLimited")
//      ramlConfig.traitNames("/users/{userId}", PUT) shouldBe Seq("secured")
//    }

    "request filters" in {
      val statusFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(POST)).requests.ramlContentTypes(None).filters
      statusFilterChain.requestFilters shouldBe Seq.empty

      val statusServiceFilterChain = ramlConfig.resourcesByUri("/status/test-service").methods(Method(GET)).requests.ramlContentTypes(None).filters
      statusServiceFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter shouldBe a[EnrichRequestFilter]
    }

    "response filters" in {
      val usersFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(GET)).responses(200).ramlContentTypes(None).filters
      usersFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterWrapper].filter shouldBe a[DenyResponseFilter]
      usersFilterChain.eventFilters.head.asInstanceOf[ConditionalEventFilterWrapper].filter shouldBe a[DenyEventFilter]
    }

    "request filters by contentType" in {
      val resourceStateContentType = Some("app-server-status")
      val resourceUpdateContentType = Some("app-server-status-update")

      val resourceStateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceStateContentType.map(ContentType)).filters
      resourceStateFilters.requestFilters shouldBe Seq.empty
      val resourceUpdateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceUpdateContentType.map(ContentType)).filters
      resourceUpdateFilters.requestFilters shouldBe Seq.empty
      val defaultFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(None).filters
      defaultFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter shouldBe a[EnrichRequestFilter]
    }

    "annotations on nested fields" in {
      val responseFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).responses(200).ramlContentTypes(None).filters
      responseFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterWrapper].filter shouldBe a[DenyResponseFilter]

      val requestFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).requests.ramlContentTypes(None).filters
      requestFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter shouldBe a[EnrichRequestFilter]
    }

    "annotations on parent resource" in {
      val parentRewriteFilter = ramlConfig.resourcesByUri("/parent").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter
      parentRewriteFilter shouldBe a[RewriteRequestFilter]
      parentRewriteFilter.asInstanceOf[RewriteRequestFilter].args.getUri shouldBe "/revault/content/some-service"

      val childRewriteFilter = ramlConfig.resourcesByUri("/parent/child").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter
      childRewriteFilter shouldBe a[RewriteRequestFilter]
      childRewriteFilter.asInstanceOf[RewriteRequestFilter].args.getUri shouldBe "/revault/content/some-service/child"
    }

    "annotations on external child resource" in {
      val childRewriteFilters = ramlConfig.resourcesByUri("/parent/external-child").filters.requestFilters
      childRewriteFilters shouldBe empty
    }

    "request URI substitution" in {
      val parameterRegularMatch = ramlConfig.resourceUri(Uri("/unreliable-feed/someContent"))
      parameterRegularMatch shouldBe Uri("/unreliable-feed/{content}", Map("content" → "someContent"))

      val strictUriMatch = ramlConfig.resourceUri(Uri("/unreliable-feed/someContent/someDetails"))
      strictUriMatch shouldBe Uri("/unreliable-feed/someContent/someDetails")

      val parameterPathMatch = ramlConfig.resourceUri(Uri("/reliable-feed/someContent/someDetails"))
      parameterPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))

      val parameterArgPathMatch = ramlConfig.resourceUri(Uri("/reliable-feed/someContent/{arg}", Map("arg" → "someDetails")))
      parameterArgPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
    }

    "filter (annotation) with arguments" in {
      val rs0 = ramlConfig.resourcesByUri("/test-rewrite/some-service")
      rs0.filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter shouldBe a[RewriteRequestFilter]

      val rs1 = ramlConfig.resourcesByUri("/test-rewrite-method/some-service")
      rs1.filters.requestFilters shouldBe empty

      val rs2 = ramlConfig.resourcesByUri("/test-rewrite-method/some-service").methods(Method(PUT))
      rs2.methodFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterWrapper].filter shouldBe a[RewriteRequestFilter]
    }
  }
}
