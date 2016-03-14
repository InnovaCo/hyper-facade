package eu.inn.facade.raml

import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}

class UriMatcherTest extends FreeSpec with Matchers {

  "URI matcher" - {
    "match URI with pattern from RAML" in {
      val unreliableResourceUri = UriMatcher.matchUri("/unreliable-feed/{content}", Uri("/unreliable-feed/someContent"))
      unreliableResourceUri shouldBe Some(Uri("/unreliable-feed/{content}", Map("content" → "someContent")))

      val reliableResourceUri = UriMatcher.matchUri("/reliable-feed/{content:*}", Uri("/reliable-feed/someContent/someDetails"))
      reliableResourceUri shouldBe Some(Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails")))

      val revaultUri = UriMatcher.matchUri("/revault/content/{path:*}", Uri("/revault/content/abc"))
      revaultUri shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))
    }
  }
}
