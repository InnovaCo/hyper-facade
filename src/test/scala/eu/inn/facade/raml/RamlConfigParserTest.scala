package eu.inn.facade.raml

import com.typesafe.config.Config
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.filter.raml._
import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.Method._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

class RamlConfigParserTest extends FreeSpec with Matchers with Injectable {
  val ramlConfig = readConfig

  def readConfig: RamlConfig = {
    implicit val injector = Injectors()
    new ConfigsFactory().ramlConfig(inject[Config])
  }

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
      statusServiceFilterChain.requestFilters.head shouldBe a[EnrichRequestFilter]
    }

    "response filters" in {
      val usersFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(GET)).responses(200).ramlContentTypes(None).filters
      usersFilterChain.responseFilters.head shouldBe a[ResponsePrivateFilter]
      usersFilterChain.eventFilters.head shouldBe a[EventPrivateFilter]
    }

    "request filters by contentType" in {
      val resourceStateContentType = Some("app-server-status")
      val resourceUpdateContentType = Some("app-server-status-update")

      val resourceStateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceStateContentType.map(ContentType)).filters
      resourceStateFilters.requestFilters shouldBe Seq.empty
      val resourceUpdateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceUpdateContentType.map(ContentType)).filters
      resourceUpdateFilters.requestFilters shouldBe Seq.empty
      val defaultFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(None).filters
      defaultFilters.requestFilters.head shouldBe a[EnrichRequestFilter]
    }

    "nested fields" in {
      val responseFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).responses(200).ramlContentTypes(None).filters
      responseFilterChain.responseFilters.head shouldBe a[ResponsePrivateFilter]

      val requestFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).requests.ramlContentTypes(None).filters
      requestFilterChain.requestFilters.head shouldBe a[EnrichRequestFilter]
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
      rs2.methodFilters.requestFilters.head shouldBe a[RewriteRequestFilter]
    }
  }
}
