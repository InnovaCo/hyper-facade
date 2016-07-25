package eu.inn.facade.filter.model

import eu.inn.facade.model.{ContextStorage, FacadeHeaders, FacadeRequest, FacadeRequestContext}
import eu.inn.facade.model.ContextStorage._
import eu.inn.parser.{EvaluationEngine, ExpressionEngine, MapBasedEvaluationEngine}

trait PredicateEvaluator {
  def evaluate(predicate: String, request: FacadeRequest, context: FacadeRequestContext): Boolean = {
    ExpressionEngine(evaluationEngine(request, context)).evaluatePredicate(predicate)
  }

  def evaluationEngine(request: FacadeRequest, context: FacadeRequestContext): EvaluationEngine
}

class MapBackedPredicateEvaluator extends PredicateEvaluator {
  override def evaluationEngine(request: FacadeRequest, context: FacadeRequestContext): EvaluationEngine = {
    MapBasedEvaluationEngine(asMap(request, context))
  }

  def asMap(request: FacadeRequest, context: FacadeRequestContext): Map[String, Any] = {
    val valueMap = Map.newBuilder[String, Any]

    val authUserMap = context.authUser match {
      case Some(authUser) ⇒
        Map[String, Any](
          "id" → authUser.id,
          "roles" → authUser.roles
        )
      case None ⇒ Map.empty[String, Any]
    }
    val contextMap = Map[String, Any](
      ContextStorage.AUTH_USER → authUserMap,
      ContextStorage.IS_AUTHORIZED → context.isAuthorized,
      "ip" → (context.requestHeaders.get(FacadeHeaders.CLIENT_IP) match {
        case Some(ip :: _) ⇒ ip
        case _ ⇒ None
      })
    )
    valueMap += ("context" → contextMap)

    val uriMap = request.uri.args.foldLeft(Map.newBuilder[String, Any]) { (acc, kv) ⇒
      val (key, value) = kv
      acc += (key → value.specific)
    }.result()
    valueMap += ("uri" → uriMap)

    valueMap.result()
  }
}
