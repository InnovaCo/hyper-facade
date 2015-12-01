package eu.inn.facade.filter

class FilterNotPassedException(val httpResponseCode: Int, val message: String) extends RuntimeException {
  def this(message: String) = this(403, message)
}
