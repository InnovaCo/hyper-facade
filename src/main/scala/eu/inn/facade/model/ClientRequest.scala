package eu.inn.facade.model

import eu.inn.hyperbus.model.DynamicRequest

case class ClientRequest(underlyingRequest: DynamicRequest)

object ClientSpecificMethod {
  val SUBSCRIBE = "subscribe"
  val UNSUBSCRIBE = "unsubscribe"
}
