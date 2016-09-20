package eu.inn.facade.raml

import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.filter.model.{ConditionalEventFilterProxy, ConditionalRequestFilterProxy, ConditionalResponseFilterProxy}
import eu.inn.facade.filter.raml._
import eu.inn.facade.modules.TestInjectors
import eu.inn.facade.raml.Method._
import eu.inn.facade.workers.TestWsRestServiceApp
import eu.inn.facade.{FacadeConfigPaths, TestBase}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service

class RamlConfigurationBuilderTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-config-parser-test.raml")
  implicit val injector = TestInjectors()
  val ramlConfig = inject[RamlConfiguration]
  val ramlReader = inject[RamlConfigurationReader]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  "RamlConfig" - {

    "request data structure" in {
      val headers = Seq()
      val getBody = TypeDefinition("ServiceStatusRequest", Some("object"), Seq(), Seq(Field("serviceType", TypeDefinition.DEFAULT_TYPE_NAME, Seq())))
      val serviceStatusGetContentType = ramlConfig.resourcesByUri("/service-status").methods(Method(GET)).requests.ramlContentTypes(None)
      serviceStatusGetContentType shouldBe RamlContentTypeConfig(headers, getBody, FilterChain.empty)

      val postBody = TypeDefinition(
        "TestRequest",
        Some("object"),
        Seq(),
        Seq(Field("mode", TypeDefinition.DEFAULT_TYPE_NAME, Seq()),
          Field("resultType", TypeDefinition.DEFAULT_TYPE_NAME, Seq()),
          Field("clientIp", TypeDefinition.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_IP, None))),
          Field("clientLanguage", TypeDefinition.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_LANGUAGE, None)))
        )
      )
      val serviceStatusPostContentType = ramlConfig.resourcesByUri("/service-status").methods(Method(POST)).requests.ramlContentTypes(None)
      serviceStatusPostContentType.typeDefinition shouldBe RamlContentTypeConfig(headers, postBody, FilterChain.empty).typeDefinition
      serviceStatusPostContentType.filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "response data structure" in {
      val headers = Seq()
      val response200Body = TypeDefinition(
        "ServiceStatusResponse",
        Some("object"),
        Seq(),
        Seq(Field("statusCode", "number", Seq()),
            Field("processedBy", TypeDefinition.DEFAULT_TYPE_NAME, Seq(DenyAnnotation(predicate = Some("!context.isAuthorized"))))
        )
      )
      val serviceStatusResponse200ContentType = ramlConfig.resourcesByUri("/service-status").methods(Method(GET)).responses(200).ramlContentTypes(None)
      serviceStatusResponse200ContentType.typeDefinition shouldBe RamlContentTypeConfig(headers, response200Body, FilterChain.empty).typeDefinition
      serviceStatusResponse200ContentType.filters.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]
      serviceStatusResponse200ContentType.filters.eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[DenyEventFilter]

      val response404 = ramlConfig.resourcesByUri("/status/test-service").methods(Method(GET)).responses.get(404)
      response404 shouldBe None
    }

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

      val status = TypeDefinition(
        "ReliableResourceState",
        Some("object"),
        Seq(),
        Seq(Field("revisionId", "number", Seq()),
          Field("content", TypeDefinition.DEFAULT_TYPE_NAME, Seq())
        )
      )
      val statusUpdate = TypeDefinition(
        "ReliableResourceUpdate",
        Some("object"),
        Seq(),
        Seq(Field("revisionId", "number", Seq()),
          Field("update", TypeDefinition.DEFAULT_TYPE_NAME, Seq())
        )
      )
      val defaultPostBody = TypeDefinition(
        "TestRequest",
        Some("object"),
        Seq(),
        Seq(Field("mode", TypeDefinition.DEFAULT_TYPE_NAME, Seq()),
          Field("resultType", TypeDefinition.DEFAULT_TYPE_NAME, Seq()),
          Field("clientIp", TypeDefinition.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_IP, None))),
          Field("clientLanguage", TypeDefinition.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_LANGUAGE, None)))
        )
      )

      val appServerStatusContentType = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceStateContentType.map(ContentType))
      appServerStatusContentType.typeDefinition shouldBe status
      appServerStatusContentType.filters.requestFilters shouldBe Seq.empty

      val appServerStatusUpdateContentType = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceUpdateContentType.map(ContentType))
      appServerStatusUpdateContentType.typeDefinition shouldBe statusUpdate
      appServerStatusUpdateContentType.filters.requestFilters shouldBe Seq.empty

      val defaultContentType = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(None)
      defaultContentType.typeDefinition shouldBe defaultPostBody
      defaultContentType.filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
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
      parentRewriteFilter.asInstanceOf[RewriteRequestFilter].uri shouldBe "/revault/content/some-service"

      val childRewriteFilter = ramlConfig.resourcesByUri("/parent/child").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
      childRewriteFilter shouldBe a[RewriteRequestFilter]
      childRewriteFilter.asInstanceOf[RewriteRequestFilter].uri shouldBe "/revault/content/some-service/child"
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
      assert(fields.exists(_.name == "statusCode"))
      assert(fields.exists(_.name == "processedBy"))
      assert(fields.exists(_.name == "timestamp"))
    }
  }
}
