package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}

class RamlConfigTest extends FreeSpec with Matchers {

  "RamlConfig" - {
    "simple traits reading" in {
      val config = ConfigFactory.load()
      val factory = new JavaNodeFactory
      val ramlConfig = RamlConfig(factory.createApi(config.getString("inn.facade.raml.file")))
      ramlConfig.traits("/status", "get") shouldBe Set("rateLimited")
      ramlConfig.traits("/users", "get") shouldBe Set("paged", "rateLimited", "secured")
      ramlConfig.traits("/users", "post") shouldBe Set("secured")
      ramlConfig.traits("/status/test-service", "get") shouldBe Set("paged")
    }
  }
}

object RamlConfigTest extends App {
  val config = ConfigFactory.load()
  val factory = new JavaNodeFactory
  val fileRelativePath = config.getString("inn.facade.raml.file")
  val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
  val file = Paths.get(fileUri).toFile
  val api = factory.createApi(file.getCanonicalPath)
  val ramlConfig = RamlConfig(api)
  println(ramlConfig.traits("/status", "get") == Set("rateLimited"))
  println(ramlConfig.traits("/users", "get"))
  println(ramlConfig.traits("/users", "post"))
  println(ramlConfig.traits("/status/test-service", "get"))
}
