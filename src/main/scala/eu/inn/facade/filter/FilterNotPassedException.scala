package eu.inn.facade.filter

case class FilterNotPassedException(httpResponseCode: Int, message: String) extends RuntimeException