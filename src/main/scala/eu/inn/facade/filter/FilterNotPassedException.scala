package eu.inn.facade.filter

class FilterNotPassedException(val httpResponseCode: Int, val message: String) extends RuntimeException {
  def this(message: String) = this(403, message) // review: надо ли по умолчанию 403 forbidden?
}

object FilterNotPassedException {

  def unapply(exception: FilterNotPassedException): Option[(Int, String)] = {
    Some(exception.httpResponseCode, exception.message)
  }
}
