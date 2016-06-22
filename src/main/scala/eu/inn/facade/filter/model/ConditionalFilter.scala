package eu.inn.facade.filter.model

import eu.inn.facade.model.{FacadeHeaders, FacadeRequest, FacadeRequestContext}
import eu.inn.facade.model.ContextStorage._
import eu.inn.parser.{EvaluationEngine, ExpressionEngine, MapBasedEvaluationEngine}

trait ConditionalFilter {
  def expressionEngine(request: FacadeRequest, context: FacadeRequestContext): ExpressionEngine = {
    ExpressionEngine(evaluationEngine(request, context))
  }

  def evaluationEngine(request: FacadeRequest, context: FacadeRequestContext): EvaluationEngine
}

trait MapBasedConditionalFilter extends ConditionalFilter {
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
      "authUser" → authUserMap,
      "isAuthorized" → context.isAuthorized,
      "ip" → (context.requestHeaders.get(FacadeHeaders.CLIENT_IP) match {
        case Some(ip :: tail) ⇒ ip
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
