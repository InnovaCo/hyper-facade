package eu.inn.facade.raml

import eu.inn.facade.modules.Injectors
import eu.inn.facade.workers.WsRestServiceApp
import eu.inn.facade.{FacadeConfigPaths, TestBase}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.servicecontrol.api.Service

class RamlConfigurationReaderTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-reader-test.raml")
  System.setProperty(FacadeConfigPaths.RAML_STRICT_CONFIG, "true")
  implicit val injector = Injectors()
  val ramlReader = inject[RamlConfigurationReader]
  val app = inject[Service].asInstanceOf[WsRestServiceApp]

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
