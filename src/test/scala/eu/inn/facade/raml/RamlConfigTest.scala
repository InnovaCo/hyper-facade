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
      ramlConfig.inputTraits("/status", "get") shouldBe Seq("rateLimited")
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
  println(ramlConfig.inputTraits("/status", "get") == Seq("rateLimited"))
  println(ramlConfig.inputTraits("/users", "get"))
  println(ramlConfig.outputTraits("/users", "get"))
  println(ramlConfig.inputTraits("/users", "post"))
  println(ramlConfig.inputTraits("/status/test-service", "get"))
}
