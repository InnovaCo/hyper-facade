package eu.inn.facade.filter.model

import scala.collection.immutable.HashMap

// review: похоже это просто case-class, почему тут class + object?
class Headers(val headers: Map[String, String], val statusCode: Option[Int]) {

  def filterNot(p: (((String, String)) ⇒ Boolean)): Headers = {
    Headers(headers.filterNot(p))
  }

  // review: обычно конкатенация :+ но в данном случае два аргумента, его как опреатор разве можно вызвать?
  def +(name: String, value: String) = {
    Headers(headers + ((name, value)), statusCode)
  }

  // review: возвращает null, вместо None, в целом сомнения, что нужен такой метод
  def → (name: String): String = {
    headers.getOrElse(name, null)
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

  def apply(statusCode: Option[Int]) = {
    new Headers(Map(), statusCode)
  }
}
