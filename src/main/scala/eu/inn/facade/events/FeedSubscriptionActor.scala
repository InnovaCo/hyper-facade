package eu.inn.facade.events

import akka.actor._
import akka.pattern.pipe
import com.typesafe.config.Config
import eu.inn.facade.FacadeConfig
import eu.inn.facade.http.RequestProcessor
import eu.inn.facade.model._
import eu.inn.facade.raml.Method
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RegexMatcher, TextMatcher}
import eu.inn.hyperbus.transport.api.uri._
import org.slf4j.LoggerFactory
import scaldi.Injector

import scala.concurrent.ExecutionContext

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperbus: Hyperbus,
                            subscriptionManager: SubscriptionsManager)
                       (implicit val injector: Injector)
  extends Actor
  with Stash
  with RequestProcessor {

  val maxResubscriptionsCount = inject[Config].getInt(FacadeConfig.MAX_RESUBSCRIPTIONS)
  val log = LoggerFactory.getLogger(getClass)
  val executionContext = inject[ExecutionContext] // don't make this implicit

  def receive: Receive = stopStartSubscription orElse {
    case request : FacadeRequest ⇒ {
      implicit val ec = executionContext
      processRequestToFacade(request) pipeTo websocketWorker
    }
  }

  def subscribing(originalRequest: FacadeRequest, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(originalRequest, event)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(originalRequest, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      context.become(subscribedReliable(originalRequest, lastRevision, subscriptionSyncTries) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Reliable subscription started for $originalRequest with revision $lastRevision")

    case BecomeUnreliable ⇒
      context.become(subscribedUnreliable(originalRequest) orElse stopStartSubscription)
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

  def stopStartSubscription: Receive = {
    case request @ FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _) ⇒
      startSubscription(request, 0)

    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)
  }

  def startSubscription(originalRequest: FacadeRequest, subscriptionSyncTries: Int): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Starting subscription #$subscriptionSyncTries for ${originalRequest.uri}")
    }
    if (subscriptionSyncTries > maxResubscriptionsCount) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxResubscriptionsCount) for $originalRequest")
      context.stop(self)
    }
    subscriptionManager.off(self)
    context.become(subscribing(originalRequest, subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext
    beforeFilterChain.filterRequest(originalRequest, originalRequest) flatMap { r ⇒
      processRequestWithRaml(originalRequest, r, 0) map { filteredRequest ⇒
        val correlationId = filteredRequest.headers.getOrElse(Header.CORRELATION_ID,
          filteredRequest.headers(Header.MESSAGE_ID)).head
        val subscriptionUri = getSubscriptionUri(filteredRequest)
        val newSubscriptionId = subscriptionManager.subscribe(self, subscriptionUri, correlationId)
        implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
        hyperbus <~ filteredRequest.copy(method = Method.GET).toDynamicRequest recover {
          handleHyperbusExceptions(originalRequest)
        } pipeTo self
      }
    } recover handleFilterExceptions(originalRequest) { response ⇒
      websocketWorker ! response
      self ! PoisonPill
    }
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

    implicit val ec = executionContext
    ramlFilterChain.filterResponse(originalRequest, facadeResponse) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(originalRequest, filteredResponse) map { finalResponse ⇒
        websocketWorker ! finalResponse
        if (finalResponse.status > 399) { // failed
          PoisonPill
        }
        else {
          finalResponse.headers.get(FacadeHeaders.CLIENT_REVISION) match {
            // reliable feed
            case Some(revision :: tail) ⇒
              BecomeReliable(revision.toLong)

            // unreliable feed
            case _ ⇒
              BecomeUnreliable
          }
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
    implicit val ec = executionContext
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
          context.become(subscribedReliable(originalRequest, lastRevisionId + 1, 0) orElse stopStartSubscription)

          implicit val ec = executionContext
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

  //  todo: this method is hacky and revault specific, elaborate more (move to revault filter?)
  //  in this case we allow regular expression in URL
  def getSubscriptionUri(filteredRequest: FacadeRequest): Uri = {
    val uri = filteredRequest.uri
    if (filteredRequest.body.asMap.contains("page.from")) {
      val newArgs: Map[String, TextMatcher] = UriParser.tokens(uri.pattern.specific).flatMap {
        case ParameterToken(name, PathMatchType) ⇒
          Some(name → RegexMatcher(uri.args(name).specific + "/.*"))

        case ParameterToken(name, RegularMatchType) ⇒
          Some(name → uri.args(name))

        case _ ⇒ None
      }.toMap
      Uri(uri.pattern, newArgs)
    }
    else {
      uri
    }
  }
}

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
    subscriptionManager))
}
