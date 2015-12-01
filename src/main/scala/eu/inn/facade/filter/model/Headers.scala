package eu.inn.facade.filter.model

import scala.collection.immutable.HashMap

class Headers(val headers: Map[String, String], val statusCode: Option[Int]) {

  def +(name: String, value: String) = {
    Headers(headers + ((name, value)), statusCode)
  }

  def → (name: String): String = {
    headers(name)
  }

  def withResponseCode(newResponseCode: Option[Int]) = {
    Headers(headers, newResponseCode)
  }

  def hasStatusCode: Boolean = {
    statusCode match {
      case Some(_) ⇒ true
      case None ⇒ false
    }
  }
}

object Headers {
  def apply(headers: Map[String, String], statusCode: Option[Int]) = {
    new Headers(headers, statusCode)
  }

  def apply(headers: Map[String, String]) = {
    new Headers(headers, None)
  }
}
