package eu.inn.facade.utils

object FunctionUtils {

  def chain[T](first: (T) ⇒ T, second: (T) ⇒ T): (T) ⇒ T = {
    { x ⇒
      second(first(x))
    }
  }
}
