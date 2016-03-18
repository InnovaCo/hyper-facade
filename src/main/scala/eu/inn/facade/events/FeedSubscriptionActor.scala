package eu.inn.facade.events

import akka.actor._
import com.typesafe.config.Config
import eu.inn.facade.http.RequestProcessor
import eu.inn.facade.model._
import eu.inn.facade.raml.Method
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import eu.inn.hyperbus.model._
import org.slf4j.LoggerFactory
import akka.pattern.pipe
import scaldi.Injector

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperBus: HyperBus,
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
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case event: DynamicRequest ⇒
      processEventWhileSubscribing(originalRequest, event)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(originalRequest, resourceState, subscriptionSyncTries)
  }

  def subscribedReliable(request: FacadeRequest, lastRevisionId: Long, subscriptionSyncTries: Int): Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case event: DynamicRequest ⇒
      processReliableEvent(request, event, lastRevisionId, subscriptionSyncTries)
  }

  def subscribedUnreliable(request: FacadeRequest): Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case event: DynamicRequest ⇒
      processUnreliableEvent(request, event)
  }

  def startSubscription(originalRequest: FacadeRequest, subscriptionSyncTries: Int): Unit = {
    if (subscriptionSyncTries > maxResubscriptionsCount) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxResubscriptionsCount) for $originalRequest")
      context.stop(self)
    }
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None

    beforeFilterChain.filterRequest(originalRequest, originalRequest) flatMap { r ⇒
      processRequestWithRaml(originalRequest, r, 0) map { filteredRequest ⇒
        val correlationId = filteredRequest.headers.getOrElse(Header.CORRELATION_ID,
          filteredRequest.headers(Header.MESSAGE_ID)).head

        implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
        this.subscriptionId = Some(subscriptionManager.subscribe(filteredRequest.uri, self, correlationId))
        context.become(subscribing(originalRequest, subscriptionSyncTries))
        hyperBus <~ filteredRequest.copy(method = Method.GET).toDynamicRequest recover {
          handleHyperbusExceptions(originalRequest)
        } pipeTo self
        Unit
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      websocketWorker ! response
      context.stop(self)
      Unit
    }
  }

  def processEventWhileSubscribing(originalRequest: FacadeRequest, event: DynamicRequest): Unit = {
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

    ramlFilterChain.filterResponse(originalRequest, facadeResponse) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(originalRequest, filteredResponse) map { finalResponse ⇒
        websocketWorker ! finalResponse
        finalResponse.headers.get(FacadeHeaders.CLIENT_REVISION) match {
          // reliable feed
          case Some(revision :: tail) ⇒
            context.become(subscribedReliable(originalRequest, revision.toLong, subscriptionSyncTries))

          // unreliable feed
          case _ ⇒
            context.become(subscribedUnreliable(originalRequest))
        }
        unstashAll()
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      websocketWorker ! response
      context.stop(self)
    }
  }

  def processUnreliableEvent(originalRequest: FacadeRequest, event: DynamicRequest): Unit = {
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

    ramlFilterChain.filterEvent(originalRequest, FacadeRequest(event)) flatMap { e ⇒
      afterFilterChain.filterEvent(originalRequest, e) map { filteredRequest ⇒

        val revisionId = filteredRequest.headers(FacadeHeaders.CLIENT_REVISION).head.toLong

        if (revisionId == lastRevisionId + 1) {
          websocketWorker ! filteredRequest
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          startSubscription(originalRequest, subscriptionSyncTries + 1)
        }
        // if revisionId <= lastRevisionId -- just ignore this event
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for request $originalRequest with filter response $response")
      }
    }
  }

  override def postStop(): Unit = {
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
  }
}

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperBus: HyperBus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperBus,
    subscriptionManager))
}
