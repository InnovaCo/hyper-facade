package eu.inn.facade.utils

import eu.inn.binders.naming._

object NamingUtils {
  val httpToFacade = new HyphenCaseToCamelCaseConverter
  val facadeToHttp = new CamelCaseToHyphenCaseConverter
}
