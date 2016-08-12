package eu.inn.facade.filter.parser

import eu.inn.authentication.AuthUser
import eu.inn.binders.value.{Null, Text}
import eu.inn.facade.model._
import eu.inn.hyperbus.model.Method
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}

class ParserTest extends FreeSpec with Matchers {

  "Parsers" - {
    "PredicateEvaluator. ip in range" in {
      val request = FacadeRequest(
        Uri("/auth-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val context = FacadeRequestContext("someIp", spray.http.Uri.Empty, "path", "get", Map(
        FacadeHeaders.CLIENT_IP → Seq("109.207.13.2")
      ), None, Map(
        ContextStorage.IS_AUTHORIZED → true,
        ContextStorage.AUTH_USER → AuthUser("id", Set("qa"), Null)
      ))
      val cwr = ContextWithRequest(context, request)

      PredicateEvaluator().evaluate(""""109.207.13.0 - 109.207.13.255" has context.ip""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.13.0 - 109.207.13.255" has "109.207.13.2"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.10.0 - 109.207.13.1" has context.ip""", cwr) shouldBe false
      PredicateEvaluator().evaluate(""""109.207.13.0/24" has context.ip""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""""109.207.13.0/24" has "109.207.13.255"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.13.0/24" has "109.207.14.0"""", cwr) shouldBe false
      PredicateEvaluator().evaluate(""""109.207.12.0/24" has context.ip""", cwr) shouldBe false
    }
  }
}

