package eu.inn.facade.raml

import eu.inn.hyperbus.transport.api.uri.{PathMatchType, RegularMatchType, Token, _}

import scala.annotation.tailrec

object UriMatcher {

  /**
    * Matches URI pattern from RAML configuration with request URI
    * @param pattern
    * @param uri
    * @return if request URI matches pattern then Some of constructed URI with parameters will be returned, None otherwise
    */
  def matchUri(pattern: String, uri: Uri): Option[Uri] = {
    val requestUriTokens = UriParser.tokens(uri.pattern.specific)
    var args = Map[String, String]()
    val patternTokens = UriParser.tokens(pattern)
    val patternTokenIter = patternTokens.iterator
    val reqUriTokenIter = requestUriTokens.iterator
    var matchesCorrectly = true
    while(patternTokenIter.hasNext && reqUriTokenIter.hasNext && matchesCorrectly) {
      val reqUriToken = reqUriTokenIter.next()
      patternTokenIter.next() match {
        case patternToken @ (TextToken(_) | SlashToken) ⇒
          matchesCorrectly = (patternToken == reqUriToken) &&
                 (patternTokenIter.hasNext == reqUriTokenIter.hasNext)

        case ParameterToken(paramName, RegularMatchType) ⇒ reqUriToken match {
          case requestUriToken @ TextToken(value) ⇒
            args += paramName → value
            matchesCorrectly = patternTokenIter.hasNext == reqUriTokenIter.hasNext
        }

        case patternToken @ ParameterToken(paramName, PathMatchType) ⇒ reqUriToken match {
          case requestUriToken @ TextToken(value) ⇒ args += paramName → foldUriTail(value, reqUriTokenIter)
        }
      }
    }
    if (!matchesCorrectly) None
    else Some(Uri(pattern, args))
  }

  /**
    * It's like toString for iterator of URI tokens
    * @param uriTail
    * @param tokenIter
    * @return
    */
  @tailrec private def foldUriTail(uriTail: String, tokenIter: Iterator[Token]): String = {
    if (tokenIter.hasNext) {
      val tokenStr = tokenIter.next() match {
        case TextToken(value) ⇒ value
        case SlashToken ⇒ "/"
      }
      foldUriTail(uriTail + tokenStr, tokenIter)
    } else uriTail
  }
}
