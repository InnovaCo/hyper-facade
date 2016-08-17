package eu.inn.facade.filter.parser

import eu.inn.authentication.AuthUser
import eu.inn.binders.value.{Null, Text}
import eu.inn.facade.model._
import eu.inn.hyperbus.model.Method
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}

class ParserTest extends FreeSpec with Matchers {

  "PredicateEvaluator" - {
    "ip in range" in {
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

      PredicateEvaluator().evaluate("""context.ip ip matches "109.207.13.0 - 109.207.13.255"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.13.2" ip matches "109.207.13.0 - 109.207.13.255"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" context.ip ip matches "109.207.10.0 - 109.207.13.1"""", cwr) shouldBe false
      PredicateEvaluator().evaluate("""context.ip ip matches "109.207.13.0/24"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""""109.207.13.255" ip matches "109.207.13.0/24"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.14.0" ip matches "109.207.13.0/24"""", cwr) shouldBe false
      PredicateEvaluator().evaluate("""context.ip ip matches "109.207.12.0/24"""", cwr) shouldBe false
    }
  }
}

