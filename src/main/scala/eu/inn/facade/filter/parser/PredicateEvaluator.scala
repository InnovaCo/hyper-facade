package eu.inn.facade.filter.parser

import eu.inn.binders.value.{Obj, _}
import eu.inn.facade.model.ContextStorage._
import eu.inn.facade.model._
import eu.inn.parser.HEval
import eu.inn.parser.ast.Identifier
import eu.inn.parser.eval.ValueContext
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

class PredicateEvaluator {
  import PredicateEvaluator._

  val log = LoggerFactory.getLogger(getClass)

  def evaluate(predicate: String, contextWithRequest: ContextWithRequest): Boolean = {
    val context = new ValueContext(contextWithRequest.toObj) {
      override def binaryOperation: PartialFunction[(Value, Identifier, Value), Value] = IpParser.binaryOperation
      override def customOperators = Seq(IpParser.IP_MATCHES)
    }
    HEval(predicate, context) match {
      case Success(value: Bool) ⇒
        value.v
      case Failure(ex) ⇒
        log.error(s"predicate '$predicate' was parsed with error", ex)
        false
    }
  }
}

object PredicateEvaluator {
  def apply(): PredicateEvaluator = {
    new PredicateEvaluator
  }

  implicit class ObjectGenerator(contextWithRequest: ContextWithRequest) {
    def toObj: Obj = {
      val valueMap = Map.newBuilder[String, Value]
      val context = contextWithRequest.context
      val request = contextWithRequest.request

      val authUserMap = context.authUser match {
        case Some(authUser) ⇒
          Map[String, Value](
            "id" → authUser.id.toValue,
            "roles" → authUser.roles.toValue
          )
        case None ⇒ Map.empty[String, Value]
      }
      val contextMap = Map[String, Value](
        ContextStorage.AUTH_USER → authUserMap,
        ContextStorage.IS_AUTHORIZED → context.isAuthorized.toValue,
        "ip" → context.remoteAddress
      )
      valueMap += ("context" → contextMap)

      val uriMap = request.uri.args.foldLeft(Map.newBuilder[String, Value]) { (acc, kv) ⇒
        val (key, value) = kv
        acc += (key → value.specific.toValue)
      }.result()
      valueMap += ("uri" → uriMap)
      Obj(valueMap.result())
    }
  }
}
