package eu.inn.facade.raml

import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable

class RamlConfigurationReaderTest extends FreeSpec with Matchers with BeforeAndAfterAll with Injectable {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-reader-test.raml")
  System.setProperty(FacadeConfigPaths.RAML_STRICT_CONFIG, "true")
  implicit val injector = Injectors()
  val ramlReader = inject[RamlConfigurationReader]

  override def afterAll(): Unit = {
    System.setProperty(FacadeConfigPaths.RAML_STRICT_CONFIG, "false")
  }

  "RamlConfigurationReader" - {
    "missing resource" in {
      intercept[RamlStrictConfigException] {
        ramlReader.resourceUri(Uri("/missing-resource"), "get")
      }
    }

    "existing resource, not configured method" in {
      ramlReader.resourceUri(Uri("/resource"), "get")
      intercept[RamlStrictConfigException] {
        ramlReader.resourceUri(Uri("/resource"), "post")
      }
    }
  }
}
