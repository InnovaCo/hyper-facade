package eu.inn.facade.http

import akka.actor.Actor
import akka.actor.Actor.Receive
import spray.http.HttpRequest

class FilterActor extends Actor {

  override def receive: Receive = {
    case request @ HttpRequest(method, uri, httpHeaders, body, protocol) ⇒
  }
}
