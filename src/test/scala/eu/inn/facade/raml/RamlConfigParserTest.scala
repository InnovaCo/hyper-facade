package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.ConfigFactory
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.filter.raml._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.Annotation._
import eu.inn.facade.raml.DataType._
import eu.inn.facade.raml.Method._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

class RamlConfigParserTest extends FreeSpec with Matchers with Injectable {
  implicit val injector = Injectors()
  val ramlConfig = readConfig

  def readConfig:RamlConfig = {
    val config = ConfigFactory.load()
    val factory = new JavaNodeFactory
    val fileRelativePath = config.getString("inn.facade.raml.file")
    val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
    val file = Paths.get(fileUri).toFile
    val api = factory.createApi(file.getCanonicalPath)
    RamlConfigParser(api).parseRaml
  }

  "RamlConfig" - {
    "traits" in {
      ramlConfig.traitNames("/status", POST) shouldBe Seq("rateLimited")
      ramlConfig.traitNames("/users", GET) shouldBe Seq("rateLimited", "secured")
      ramlConfig.traitNames("/users", POST) shouldBe Seq("secured")
    }

    "request data structure" in {
      val usersHeaders = Seq(Header("authToken"))
      val usersBody = Body(DataType("StatusRequest", Seq(Field("serviceType", DataType())), Seq()))
      val dsUsers = ramlConfig.resourcesByUri("/users").methods(Method(GET)).requests.dataStructures(None)
      dsUsers shouldBe DataStructure(usersHeaders, Some(usersBody), FilterChain.empty)

      val testServiceHeaders = Seq(Header("authToken"))
      val testServiceBody = Body(
        DataType("TestRequest",
          Seq(Field("mode", DataType()),
              Field("resultType", DataType()),
              Field("clientIP", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(CLIENT_IP)))),
              Field("clientLanguage", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(CLIENT_LANGUAGE))))),
          Seq()))

      val ds = ramlConfig.resourcesByUri("/status/test-service").methods(Method(GET)).requests.dataStructures(None)
      ds.copy(filters = FilterChain.empty) shouldBe
        DataStructure(testServiceHeaders, Some(testServiceBody), FilterChain.empty)

      ds.filters.requestFilters.head shouldBe a[EnrichRequestFilter]
    }

    "response data structure" in {
      val usersHeaders = Seq(Header("content-type"))
      val usersBody = Body(
        DataType("Status",
          Seq(Field("statusCode", DataType("number", Seq(), Seq())),
              Field("processedBy", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(PRIVATE))))),
          Seq()))
      val dsUsers = ramlConfig.resourcesByUri("/users").methods(Method(GET)).responses(200).dataStructures(None)
      dsUsers.copy(filters = FilterChain.empty) shouldBe DataStructure(usersHeaders, Some(usersBody), FilterChain.empty)
      dsUsers.filters.responseFilters.head shouldBe a[ResponsePrivateFilter]
      dsUsers.filters.eventFilters.head shouldBe a[EventPrivateFilter]

      val testServiceHeaders = Seq(Header("content-type"))
      val testServiceBody = Body(
        DataType("Status",
          Seq(Field("statusCode", DataType("number", Seq(), Seq())),
              Field("processedBy", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(PRIVATE))))),
          Seq()))
      val dsResponse = ramlConfig.resourcesByUri("/users").methods(Method(GET)).responses(200).dataStructures(None)
      dsResponse.copy(filters = FilterChain.empty) shouldBe DataStructure(testServiceHeaders, Some(testServiceBody), FilterChain.empty)

      val test404Headers = Seq[Header]()
      val test404Body = Body(DataType())
      val dsResponse404 = ramlConfig.resourcesByUri("/status/test-service").methods(Method(GET)).responses(404).dataStructures(None)
      dsResponse404 shouldBe DataStructure(test404Headers, Some(test404Body), FilterChain.empty)
    }

    "request data structures by contentType" in {
      val feedHeaders = Seq()
      val reliableResourceStateBody = Body(
        DataType("ReliableResourceState",
          Seq(Field("revisionId", DataType("number", Seq(), Seq())),
              Field("content", DataType())),
          Seq()))
      val reliableResourceUpdateBody = Body(
        DataType("ReliableResourceUpdate",
          Seq(Field("revisionId", DataType("number", Seq(), Seq())),
              Field("update", DataType())),
          Seq()))
      val testRequestBody = Body(
        DataType("TestRequest",
          Seq(Field("mode", DataType("string", Seq(), Seq())),
              Field("resultType", DataType("string", Seq(), Seq())),
              Field("clientIP", DataType("string", Seq(), Seq(Annotation(CLIENT_IP)))),
              Field("clientLanguage", DataType("string", Seq(), Seq(Annotation(CLIENT_LANGUAGE))))),
        Seq()))
      val resourceStateContentType = Some("application/vnd+app-server-status.json")
      val resourceUpdateContentType = Some("application/vnd+app-server-status-update.json")

      val dsState = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.dataStructures(resourceStateContentType.map(ContentType))
      dsState shouldBe DataStructure(feedHeaders, Some(reliableResourceStateBody), FilterChain.empty)
      val dsUpdate = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.dataStructures(resourceUpdateContentType.map(ContentType))
      dsUpdate shouldBe DataStructure(feedHeaders, Some(reliableResourceUpdateBody), FilterChain.empty)
      val dsDefault = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.dataStructures(None)
      dsDefault.copy(filters = FilterChain.empty) shouldBe DataStructure(feedHeaders, Some(testRequestBody), FilterChain.empty)
    }

    "request URI substitution" in {
      val parameterRegularMatch = ramlConfig.resourceUri("/unreliable-feed/someContent")
      parameterRegularMatch shouldBe Uri("/unreliable-feed/{content}", Map("content" → "someContent"))

      val strictUriMatch = ramlConfig.resourceUri("/unreliable-feed/someContent/someDetails")
      strictUriMatch shouldBe Uri("/unreliable-feed/someContent/someDetails")

      val parameterPathMatch = ramlConfig.resourceUri("/reliable-feed/someContent/someDetails")
      parameterPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
    }

    "filter (annotation) with arguments" in {
      val rs0 = ramlConfig.resourcesByUri("/test-rewrite/some-service")
      rs0.filters.requestFilters.head shouldBe a[RewriteRequestFilter]

      val rs1 = ramlConfig.resourcesByUri("/test-rewrite-method/some-service")
      rs1.filters.requestFilters shouldBe empty

      val rs2 = ramlConfig.resourcesByUri("/test-rewrite-method/some-service").methods(Method(PUT))
      rs2.filters.requestFilters.head shouldBe a[RewriteRequestFilter]
    }
  }
}
