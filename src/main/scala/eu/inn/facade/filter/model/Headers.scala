package eu.inn.facade.filter.model

case class Headers(headers: Map[String, String], statusCode: Option[Int]) {

  def withStatusCode(newResponseCode: Option[Int]) = {
    Headers(headers, newResponseCode)
  }

  def hasStatusCode: Boolean = {
    statusCode match {
      case Some(_) ⇒ true
      case None ⇒ false
    }
  }
}
