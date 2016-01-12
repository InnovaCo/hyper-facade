package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}
import eu.inn.facade.raml.Annotation._

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
      ramlConfig.traitNames("/status", "get") shouldBe Seq("rateLimited")
      ramlConfig.traitNames("/users", "get") shouldBe Seq("paged", "rateLimited", "secured")
      ramlConfig.traitNames("/users", "post") shouldBe Seq("secured")
      ramlConfig.traitNames("/status/test-service", "get") shouldBe Seq("paged")
      readConfig.traitNames("/private", "get") shouldBe Seq("privateResource")
    }

    "request data structure" in {
      val usersHeaders = Seq(Header("authToken"))
      val usersBody = Body(Seq(Field("serviceType", Seq())))
      ramlConfig.requestDataStructure("/users", "get") shouldBe DataStructure(usersHeaders, usersBody)

      val testServiceHeaders = Seq(Header("authToken"))
      val testServiceBody = Body(Seq(Field("mode", Seq()), Field("resultType", Seq()),
        Field("clientIP", Seq(Annotation(CLIENT_IP))), Field("clientLanguage", Seq(Annotation(CLIENT_LANGUAGE)))))
      ramlConfig.requestDataStructure("/status/test-service", "get") shouldBe DataStructure(testServiceHeaders, testServiceBody)
    }

    "response data structure" in {
      val usersHeaders = Seq(Header("content-type"))
      val usersBody = Body(Seq(Field("statusCode", Seq()), Field("processedBy", Seq(Annotation(PRIVATE)))))
      ramlConfig.responseDataStructure("/users", "get", 200) shouldBe DataStructure(usersHeaders, usersBody)

      val testServiceHeaders = Seq(Header("content-type"))
      val testServiceBody = Body(Seq(Field("statusCode", Seq()), Field("processedBy", Seq(Annotation(PRIVATE)))))
      ramlConfig.responseDataStructure("/status/test-service", "get", 200) shouldBe DataStructure(testServiceHeaders, testServiceBody)

      val test404Headers = Seq[Header]()
      val test404Body = Body(Seq())
      ramlConfig.responseDataStructure("/status/test-service", "get", 404) shouldBe DataStructure(test404Headers, test404Body)
    }
  }
}
