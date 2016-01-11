package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}

class RamlConfigTest extends FreeSpec with Matchers {
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
      ramlConfig.traits("/status", "get") shouldBe Seq("rateLimited")
      ramlConfig.traits("/users", "get") shouldBe Seq("paged", "rateLimited", "secured")
      ramlConfig.traits("/users", "post") shouldBe Seq("secured")
      ramlConfig.traits("/status/test-service", "get") shouldBe Seq("paged")
    }

    "request data structure" in {
      val usersHeaders = Seq(Header("authToken"))
      val usersBody = Body(Seq(Field("serviceType", false)))
      ramlConfig.requestDataStructure("/users", "get") shouldBe DataStructure(usersHeaders, usersBody)

      val testServiceHeaders = Seq(Header("authToken"))
      val testServiceBody = Body(Seq(Field("mode", false), Field("resultType", false), Field("clientIP", false), Field("clientLanguage", false)))
      ramlConfig.requestDataStructure("/status/test-service", "get") shouldBe DataStructure(testServiceHeaders, testServiceBody)
    }

    "response data structure" in {
      val usersHeaders = Seq(Header("content-type"))
      val usersBody = Body(Seq(Field("statusCode", false), Field("processedBy", true)))
      ramlConfig.responseDataStructure("/users", "get", 200) shouldBe DataStructure(usersHeaders, usersBody)

      val testServiceHeaders = Seq(Header("content-type"))
      val testServiceBody = Body(Seq(Field("statusCode", false), Field("processedBy", true)))
      ramlConfig.responseDataStructure("/status/test-service", "get", 200) shouldBe DataStructure(testServiceHeaders, testServiceBody)

      val test404Headers = Seq[Header]()
      val test404Body = Body(Seq())
      ramlConfig.responseDataStructure("/status/test-service", "get", 404) shouldBe DataStructure(test404Headers, test404Body)
    }
  }
}
