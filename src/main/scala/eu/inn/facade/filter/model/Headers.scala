package eu.inn.facade.filter.model

class Headers(val headers: Seq[Header], val responseCode: Option[Int]) {

  def :+(header: Header) = {
    Headers(headers :+ header, responseCode)
  }

  def withResponseCode(newResponseCode: Option[Int]) = {
    Headers(headers, newResponseCode)
  }

  def hasResponseCode: Boolean = {
    responseCode match {
      case Some(_) ⇒ true
      case None ⇒ false
    }
  }
}

object Headers {
  def apply(headers: Seq[Header], responseCode: Option[Int]) = {
    new Headers(headers, responseCode)
  }

  def apply(headers: Seq[Header]) = {
    new Headers(headers, None)
  }
}
