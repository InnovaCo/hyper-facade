package eu.inn.facade.filter.parser

import eu.inn.binders.value.{Obj, _}
import eu.inn.facade.model.ContextStorage._
import eu.inn.facade.model._
import eu.inn.parser.HEval
import org.parboiled2.ParseError
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

class PredicateEvaluator {
  import PredicateEvaluator._

  val log = LoggerFactory.getLogger(getClass)

  def evaluate(predicate: String, contextWithRequest: ContextWithRequest): Boolean = {
    try {
      evaluateAsRegularExpression(predicate, contextWithRequest)
    } catch {
      case ParseError(_,_,_) ⇒
        evaluateAsIpExpression(predicate, contextWithRequest)
    }
  }

  def evaluateAsIpExpression(predicate: String, contextWithRequest: ContextWithRequest): Boolean = {
    IpParser(predicate).IpInRangeInputLine.run() match {
      case Success((left, op, right)) ⇒
        val ipValue = parseIp(left, contextWithRequest)
        val ipRangeValue = parseIpRange(right, contextWithRequest)
        (ipValue, ipRangeValue) match {
          case (Some(ip), Some(ipRange)) ⇒
            op match {
              case IpParser.IN_BOP ⇒
                ipRange.contains(ip)
              case IpParser.NOT_IN_BOP ⇒
                !ipRange.contains(ip)
              case otherOp ⇒
                log.error(s"Operation '$otherOp' is not supported for IP and IP range values")
                false
            }
          case _ ⇒
            false
        }
      case Failure(_) ⇒
        false
    }
  }

  def parseIp(left: String, contextWithRequest: ContextWithRequest): Option[Ip] = {
    IpParser.parseIp(left) match {
      case None ⇒
        fallbackToRegular(left, contextWithRequest) match {
          case Text(probablyIp) ⇒
            IpParser.parseIp(probablyIp)
          case _ ⇒
            None
        }
      case other ⇒
        other
    }
  }

  def parseIpRange(right: String, contextWithRequest: ContextWithRequest): Option[IpRange] = {
    IpParser.parseIpRange(right) match {
      case None ⇒
        fallbackToRegular(right, contextWithRequest) match {
          case Text(probablyIpRange) ⇒
            IpParser.parseIpRange(probablyIpRange)
          case _ ⇒
            None
        }
      case other ⇒
        other
    }
  }

  def evaluateAsRegularExpression(predicate: String, contextWithRequest: ContextWithRequest): Boolean = {
    fallbackToRegular(predicate, contextWithRequest).asInstanceOf[Bool].v
  }

  def fallbackToRegular(predicate: String, contextWithRequest: ContextWithRequest): Value = {
    HEval(predicate, contextWithRequest.toObj)
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
        "ip" → (context.requestHeaders.get(FacadeHeaders.CLIENT_IP) match {
          case Some(ip :: _) ⇒ ip.toValue
          case _ ⇒ Null
        })
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
