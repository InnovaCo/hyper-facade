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
    RamlConfig(api)
  }

  "RamlConfig" - {
    "traits" in {
      ramlConfig.traits("/status", "get") shouldBe Set("rateLimited")
      ramlConfig.traits("/users", "get") shouldBe Set("paged", "rateLimited", "secured")
      ramlConfig.traits("/users", "post") shouldBe Set("secured")
      ramlConfig.traits("/status/test-service", "get") shouldBe Set("paged")
    }

    "request data structure" in {
      val usersHeaders = Seq(Header("authToken"))
      val usersBody = Body(Seq(Field("serviceType", false)))
      ramlConfig.requestDataStructure("/users", "get") shouldBe DataStructure(usersHeaders, usersBody)

      val testServiceHeaders = Seq(Header("x_http_forwarded_for"), Header("authToken"))
      val testServiceBody = Body(Seq(Field("mode", false), Field("resultType", false)))
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
