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
      ramlConfig.traits("/status", "get") shouldBe Seq("rateLimited")
    }
  }
}

object RamlConfigTest extends App {
  val config = ConfigFactory.load()
  val factory = new JavaNodeFactory
  val fileRelativePath = config.getString("inn.facade.raml.file")
  val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
  val file = Paths.get(fileUri).toFile
  val ramlConfig = RamlConfig(factory.createApi(file.getCanonicalPath))
  println(ramlConfig.traits("/status", "get") == Seq("rateLimited"))
  println(ramlConfig.traits("/users", "get"))
  println(ramlConfig.traits("/users", "get"))
  println(ramlConfig.traits("/users", "post"))
  println(ramlConfig.traits("/status/test-service", "get"))
}
