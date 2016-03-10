package eu.inn.facade.events

case class FeedState(resourceStateFetched: Boolean, reliable: Boolean, lastRevisionId: Long, resubscriptionCount: Int)

object FeedState {
  def apply(): FeedState = {
    FeedState(false, false, 0L, 0)
  }
}
