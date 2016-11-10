package eu.inn.facade.raml

import eu.inn.facade.utils.UriMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}

class UriMatcherTest extends FreeSpec with Matchers {

  "URI matcher" - {
    "match URI with pattern from RAML" in {
      val parameterRegularMatch = UriMatcher.matchUri("/unreliable-feed/{content}", Uri("/unreliable-feed/someContent"))
      parameterRegularMatch shouldBe Some(Uri("/unreliable-feed/{content}", Map("content" → "someContent")))

      val parameterLongPathMatch = UriMatcher.matchUri("/reliable-feed/{content:*}", Uri("/reliable-feed/someContent/someDetails"))
      parameterLongPathMatch shouldBe Some(Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails")))

      val parameterShortPathMatch = UriMatcher.matchUri("/revault/content/{path:*}", Uri("/revault/content/abc"))
      parameterShortPathMatch shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))

      val pathToPathMatch = UriMatcher.matchUri("/rewritten-events/root/{path:*}", Uri("/rewritten-events/{path:*}", Map("path" → "root/1")))
      pathToPathMatch shouldBe Some(Uri("/rewritten-events/root/{path:*}", Map("path" → "1")))

      val doubleSlashRequest = UriMatcher.matchUri("/revault/content/{path:*}", Uri("/revault//content/abc"))
      doubleSlashRequest shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))

      val tripleSlashRequest = UriMatcher.matchUri("/revault/content/{path:*}", Uri("/revault///content/abc"))
      tripleSlashRequest shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))
    }
  }
}
