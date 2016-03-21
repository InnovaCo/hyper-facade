package eu.inn.facade.events

import akka.actor._
import akka.pattern.pipe
import com.typesafe.config.Config
import eu.inn.facade.http.RequestProcessor
import eu.inn.facade.model._
import eu.inn.facade.raml.Method
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model._
import org.slf4j.LoggerFactory
import scaldi.Injector

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperbus: Hyperbus,
                            subscriptionManager: SubscriptionsManager)
                       (implicit val injector: Injector)
  extends Actor
  with Stash
  with RequestProcessor {

  val maxResubscriptionsCount = inject[Config].getInt("inn.facade.maxResubscriptionsCount")
  var subscriptionId: Option[String] = None
  val log = LoggerFactory.getLogger(getClass)
  implicit def executionContext = context.dispatcher

  def receive: Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case request @ FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _) ⇒
      startSubscription(request, 0)

    case request : FacadeRequest ⇒
      processRequestToFacade(request) pipeTo websocketWorker
  }

  def subscribing(originalRequest: FacadeRequest, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(originalRequest, event)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(originalRequest, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      context.become(subscribedReliable(originalRequest, lastRevision, subscriptionSyncTries) orElse stopSubScriptionOrUpdate)
      unstashAll()
      log.debug(s"Reliable subscription started for $originalRequest with revision $lastRevision")

    case BecomeUnreliable ⇒
      context.become(subscribedUnreliable(originalRequest) orElse stopSubScriptionOrUpdate)
      unstashAll()
      log.debug(s"Unreliable subscription started for $originalRequest")
  }

  def subscribedReliable(originalRequest: FacadeRequest, lastRevisionId: Long, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(originalRequest, event, lastRevisionId, subscriptionSyncTries)

    case RestartSubscription ⇒
      startSubscription(originalRequest, subscriptionSyncTries + 1)
  }

  def subscribedUnreliable(request: FacadeRequest): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(request, event)
  }

  def stopSubScriptionOrUpdate: Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)
    case UpdateSubscriptionId(newSubscriptionId) ⇒
      subscriptionId = Some(newSubscriptionId)
  }

  def startSubscription(originalRequest: FacadeRequest, subscriptionSyncTries: Int): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Starting subscription #$subscriptionSyncTries for ${originalRequest.uri}")
    }
    if (subscriptionSyncTries > maxResubscriptionsCount) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxResubscriptionsCount) for $originalRequest")
      context.stop(self)
    }
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
    context.become(subscribing(originalRequest, subscriptionSyncTries) orElse stopSubScriptionOrUpdate)

    beforeFilterChain.filterRequest(originalRequest, originalRequest) flatMap { r ⇒
      processRequestWithRaml(originalRequest, r, 0) map { filteredRequest ⇒
        val correlationId = filteredRequest.headers.getOrElse(Header.CORRELATION_ID,
          filteredRequest.headers(Header.MESSAGE_ID)).head
        val newSubscriptionId = subscriptionManager.subscribe(filteredRequest.uri, self, correlationId)
        implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
        hyperbus <~ filteredRequest.copy(method = Method.GET).toDynamicRequest recover {
          handleHyperbusExceptions(originalRequest)
        } pipeTo self
        UpdateSubscriptionId(newSubscriptionId)
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processEventWhileSubscribing(originalRequest: FacadeRequest, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing event while subscribing $event for ${originalRequest.uri}")
    }

    event.headers.get(Header.REVISION) match {
      // reliable feed
      case Some(revision :: tail) ⇒
        log.debug(s"event $event is stashed because resource state is not fetched yet")
        stash()

      // unreliable feed
      case _ ⇒
        processUnreliableEvent(originalRequest, event)
    }
  }

  def processResourceState(originalRequest: FacadeRequest, resourceState: Response[DynamicBody], subscriptionSyncTries: Int) = {
    val facadeResponse = FacadeResponse(resourceState)
    if (log.isTraceEnabled) {
      log.trace(s"Processing resource state $resourceState for ${originalRequest.uri}")
    }

    ramlFilterChain.filterResponse(originalRequest, facadeResponse) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(originalRequest, filteredResponse) map { finalResponse ⇒
        websocketWorker ! finalResponse
        finalResponse.headers.get(FacadeHeaders.CLIENT_REVISION) match {
          // reliable feed
          case Some(revision :: tail) ⇒
            BecomeReliable(revision.toLong)

          // unreliable feed
          case _ ⇒
            BecomeUnreliable
        }
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processUnreliableEvent(originalRequest: FacadeRequest, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing unreliable event $event for ${originalRequest.uri}")
    }
    ramlFilterChain.filterEvent(originalRequest, FacadeRequest(event)) flatMap { e ⇒
      afterFilterChain.filterEvent(originalRequest, e) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for request $originalRequest with filter response $response")
      }
    }
  }

  def processReliableEvent(originalRequest: FacadeRequest, event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: tail) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing reliable event #$revisionId $event for ${originalRequest.uri}")
        }

        if (revisionId == lastRevisionId + 1) {
          context.become(subscribedReliable(originalRequest, lastRevisionId + 1, 0) orElse stopSubScriptionOrUpdate)

          ramlFilterChain.filterEvent(originalRequest, FacadeRequest(event)) flatMap { e ⇒
            afterFilterChain.filterEvent(originalRequest, e) map { filteredRequest ⇒
              websocketWorker ! filteredRequest
            }
          } recover handleFilterExceptions(originalRequest) { response ⇒
            if (log.isDebugEnabled) {
              log.debug(s"Event is discarded for request $originalRequest with filter response $response")
            }
          }
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          self ! RestartSubscription
          log.info(s"Subscription on ${originalRequest.uri} lost events from $lastRevisionId to $revisionId. Restarting...")
        }
        // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: $originalRequest")
    }
  }

  override def postStop(): Unit = {
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
  }
}

case class UpdateSubscriptionId(subscriptionId: String)
case class BecomeReliable(lastRevision: Long)
case object BecomeUnreliable
case object RestartSubscription

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperbus,
    subscriptionManager)).withDispatcher("deque-dispatcher")
}
