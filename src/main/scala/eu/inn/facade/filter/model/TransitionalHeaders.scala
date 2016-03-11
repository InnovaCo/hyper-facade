package eu.inn.facade.filter.model

import eu.inn.hyperbus.transport.api.uri.Uri

case class TransitionalHeaders(uri: Uri, headers: Map[String, Seq[String]], statusCode: Option[Int]) {

  def withStatusCode(newResponseCode: Option[Int]) = {
    TransitionalHeaders(uri, headers, newResponseCode)
  }

  def headerOption(name: String): Option[String] = {
    headers.get(name) match {
      case Some(values) ⇒ Some(values.head)
      case None ⇒ None
    }
  }

  def hasStatusCode: Boolean = {
    statusCode match {
      case Some(_) ⇒ true
      case None ⇒ false
    }
  }
}
