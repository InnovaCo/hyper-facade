package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.ConfigFactory
import eu.inn.facade.filter.chain.Filters
import eu.inn.facade.filter.raml.{PrivateFieldsEventFilter, PrivateFieldsResponseFilter, EnrichRequestFilter}
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
      ramlConfig.traitNames("/users", GET) shouldBe Seq("paged", "rateLimited", "secured")
      ramlConfig.traitNames("/users", POST) shouldBe Seq("secured")
      ramlConfig.traitNames("/status/test-service", GET) shouldBe Seq("paged")
      ramlConfig.traitNames("/private", GET) shouldBe Seq("privateResource")
    }

    "request data structure" in {
      val usersHeaders = Seq(Header("authToken"))
      val usersBody = Body(DataType("StatusRequest", Seq(Field("serviceType", DataType())), Seq()))
      ramlConfig.requestDataStructure("/users", GET, None) shouldBe Some(DataStructure(usersHeaders, Some(usersBody), Filters.empty))

      val testServiceHeaders = Seq(Header("authToken"))
      val testServiceBody = Body(
        DataType("TestRequest",
          Seq(Field("mode", DataType()),
              Field("resultType", DataType()),
              Field("clientIP", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(CLIENT_IP)))),
              Field("clientLanguage", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(CLIENT_LANGUAGE))))),
          Seq()))

      val ds = ramlConfig.requestDataStructure("/status/test-service", GET, None)
      ds.get.copy(filters = Filters.empty) shouldBe
        DataStructure(testServiceHeaders, Some(testServiceBody), Filters.empty)

      ds.get.filters.requestFilters.head shouldBe a[EnrichRequestFilter]
    }

    "response data structure" in {
      val usersHeaders = Seq(Header("content-type"))
      val usersBody = Body(
        DataType("Status",
          Seq(Field("statusCode", DataType("number", Seq(), Seq())),
              Field("processedBy", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(PRIVATE))))),
          Seq()))
      val ds = ramlConfig.responseDataStructure("/users", GET, 200)
      ds.get.copy(filters = Filters.empty) shouldBe DataStructure(usersHeaders, Some(usersBody), Filters.empty)
      ds.get.filters.responseFilters.head shouldBe a[PrivateFieldsResponseFilter]
      ds.get.filters.eventFilters.head shouldBe a[PrivateFieldsEventFilter]

      val testServiceHeaders = Seq(Header("content-type"))
      val testServiceBody = Body(
        DataType("Status",
          Seq(Field("statusCode", DataType("number", Seq(), Seq())),
              Field("processedBy", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(PRIVATE))))),
          Seq()))
      ramlConfig.responseDataStructure("/status/test-service", GET, 200).get.copy(filters = Filters.empty) shouldBe DataStructure(testServiceHeaders, Some(testServiceBody), Filters.empty)

      val test404Headers = Seq[Header]()
      val test404Body = Body(DataType())
      ramlConfig.responseDataStructure("/status/test-service", GET, 404) shouldBe Some(DataStructure(test404Headers, Some(test404Body), Filters.empty))
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

      ramlConfig.requestDataStructure("/reliable-feed/{content:*}", POST, resourceStateContentType) shouldBe Some(DataStructure(feedHeaders, Some(reliableResourceStateBody), Filters.empty))
      ramlConfig.requestDataStructure("/reliable-feed/{content:*}", POST, resourceUpdateContentType) shouldBe Some(DataStructure(feedHeaders, Some(reliableResourceUpdateBody), Filters.empty))
      ramlConfig.requestDataStructure("/reliable-feed/{content:*}", POST, None).get.copy(filters = Filters.empty) shouldBe DataStructure(feedHeaders, Some(testRequestBody), Filters.empty)
    }

    "request URI substitution" in {
      val parameterRegularMatch = ramlConfig.resourceUri("/unreliable-feed/someContent")
      parameterRegularMatch shouldBe Uri("/unreliable-feed/{content}", Map("content" → "someContent"))

      val strictUriMatch = ramlConfig.resourceUri("/unreliable-feed/someContent/someDetails")
      strictUriMatch shouldBe Uri("/unreliable-feed/someContent/someDetails")

      val parameterPathMatch = ramlConfig.resourceUri("/reliable-feed/someContent/someDetails")
      parameterPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
    }
  }
}
