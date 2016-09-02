package eu.inn.facade.raml

import eu.inn.facade.filter.model.{ConditionalEventFilterProxy, ConditionalRequestFilterProxy, ConditionalResponseFilterProxy}
import eu.inn.facade.filter.raml._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.Method._
import eu.inn.facade.workers.WsRestServiceApp
import eu.inn.facade.{FacadeConfigPaths, TestBase}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service

class RamlConfigurationBuilderTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-config-parser-test.raml")
  implicit val injector = Injectors()
  val ramlConfig = inject[RamlConfiguration]
  val ramlReader = inject[RamlConfigurationReader]
  val app = inject[Service].asInstanceOf[WsRestServiceApp]

  "RamlConfig" - {
    "request filters" in {
      val statusFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(POST)).requests.ramlContentTypes(None).filters
      statusFilterChain.requestFilters shouldBe Seq.empty

      val statusServiceFilterChain = ramlConfig.resourcesByUri("/status/test-service").methods(Method(GET)).requests.ramlContentTypes(None).filters
      statusServiceFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "response filters" in {
      val usersFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(GET)).responses(200).ramlContentTypes(None).filters
      usersFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]
      usersFilterChain.eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[DenyEventFilter]
    }

    "request filters by contentType" in {
      val resourceStateContentType = Some("app-server-status")
      val resourceUpdateContentType = Some("app-server-status-update")

      val resourceStateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceStateContentType.map(ContentType)).filters
      resourceStateFilters.requestFilters shouldBe Seq.empty
      val resourceUpdateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceUpdateContentType.map(ContentType)).filters
      resourceUpdateFilters.requestFilters shouldBe Seq.empty
      val defaultFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(None).filters
      defaultFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "annotations on nested fields" in {
      val responseFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).responses(200).ramlContentTypes(None).filters
      responseFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]

      val requestFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).requests.ramlContentTypes(None).filters
      requestFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "annotations on parent resource" in {
      val parentRewriteFilter = ramlConfig.resourcesByUri("/parent").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
      parentRewriteFilter shouldBe a[RewriteRequestFilter]
      parentRewriteFilter.asInstanceOf[RewriteRequestFilter].args.getUri shouldBe "/revault/content/some-service"

      val childRewriteFilter = ramlConfig.resourcesByUri("/parent/child").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
      childRewriteFilter shouldBe a[RewriteRequestFilter]
      childRewriteFilter.asInstanceOf[RewriteRequestFilter].args.getUri shouldBe "/revault/content/some-service/child"
    }

    "annotations on external child resource" in {
      val childRewriteFilters = ramlConfig.resourcesByUri("/parent/external-child").filters.requestFilters
      childRewriteFilters shouldBe empty
    }

    "request URI substitution" in {
      val parameterRegularMatch = ramlReader.resourceUri(Uri("/unreliable-feed/someContent"), "get")
      parameterRegularMatch shouldBe Uri("/unreliable-feed/{content}", Map("content" → "someContent"))

      val strictUriMatch = ramlReader.resourceUri(Uri("/unreliable-feed/someContent/someDetails"), "get")
      strictUriMatch shouldBe Uri("/unreliable-feed/someContent/someDetails")

      val parameterPathMatch = ramlReader.resourceUri(Uri("/reliable-feed/someContent/someDetails"), "get")
      parameterPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))

      val parameterArgPathMatch = ramlReader.resourceUri(Uri("/reliable-feed/someContent/{arg}", Map("arg" → "someDetails")), "get")
      parameterArgPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
    }

    "filter (annotation) with arguments" in {
      val rs0 = ramlConfig.resourcesByUri("/test-rewrite-method")
      rs0.filters.requestFilters shouldBe empty

      val rs1 = ramlConfig.resourcesByUri("/test-rewrite-method").methods(Method(PUT))
      rs1.methodFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[RewriteRequestFilter]
    }

    "type inheritance" in {
      val extStatusTypeDef = ramlConfig.resourcesByUri("/ext-status").methods(Method(POST)).requests.ramlContentTypes(None).typeDefinition
      val fields = extStatusTypeDef.fields
      fields.size shouldBe 3
      fields(0).name shouldBe "timestamp"
      fields(1).name shouldBe "statusCode"
      fields(2).name shouldBe "processedBy"
    }
  }
}
