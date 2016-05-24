package eu.inn.facade.utils

import eu.inn.facade.modules.Injectors
import eu.inn.facade.raml.RamlConfig
import eu.inn.facade.{CleanRewriteIndex, FacadeConfigPaths}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

class UriTransformerTest extends FreeSpec with Matchers with CleanRewriteIndex with Injectable {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "specific-raml-configs/uri-transformer-test.raml")
  implicit val injector = Injectors()
  val ramlConfig = inject[RamlConfig]

  "UriTransformerTest" - {
    "Rewrite backward" in {
      val r = UriTransformer.rewriteLinkToOriginal(Uri("/rewritten-events/root/1"), 1)
      r shouldBe Uri("/events/1")
    }

    "Rewrite backward (templated)" in {
      val r = UriTransformer.rewriteLinkToOriginal(Uri("/rewritten-events/{path:*}", Map("path" → "root/1")), 1)
      r shouldBe Uri("/events/1")
    }

    "Rewrite forward" in {
      val r = UriTransformer.rewriteLinkForward(Uri("/events/25"), 1, ramlConfig)
      r shouldBe Uri("/rewritten-events/root/{path:*}", Map("path" → "25"))
    }

    "Rewrite forward (templated)" in {
      val r = UriTransformer.rewriteLinkForward(Uri("/events/{path}",Map("path" → "25")), 1, ramlConfig)
      r shouldBe Uri("/rewritten-events/root/{path:*}", Map("path" → "25"))
    }

    "Do not rewrite forward legacy resource" in {
      val r = UriTransformer.rewriteLinkForward(Uri("/events/legacy"), 1, ramlConfig)
      r shouldBe Uri("/events/legacy")
    }
  }
}
