package eu.inn.facade.utils

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future, Promise}

object FutureUtils {
  def chain[T](input: T, futures: Seq[T ⇒ Future[T]])(implicit ec: ExecutionContext): Future[T] = {
    val promisedResult = Promise[T]()
    if (futures nonEmpty) {
      val firstInput: Future[T] = Future.successful(input)
      futures.foldLeft(firstInput) { (foldedValue, next) ⇒
        foldedValue.flatMap { result ⇒
          next(result)
        }
      } onComplete { filteredResult ⇒ promisedResult.complete(filteredResult) }
    } else {
      promisedResult.success(input)
    }
    promisedResult.future
  }
}