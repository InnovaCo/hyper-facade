package eu.inn.facade.events

import akka.actor._
import akka.pattern.pipe
import com.typesafe.config.Config
import eu.inn.facade.FacadeConfig
import eu.inn.facade.filter.FilterContext
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

  def filtering(originalRequest: FacadeRequest, subscriptionSyncTries: Int): Receive = {
    case BeforeFilterComplete(filterContext, facadeRequest) ⇒
      continueSubscription(originalRequest, filterContext, facadeRequest, subscriptionSyncTries)
  }

  def subscribing(originalRequest: FacadeRequest, filterContext: FilterContext, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(filterContext, event)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(filterContext, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      context.become(subscribedReliable(filterContext, originalRequest, lastRevision, subscriptionSyncTries) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Reliable subscription started for $originalRequest with revision $lastRevision")

    case BecomeUnreliable ⇒
      context.become(subscribedUnreliable(filterContext) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Unreliable subscription started for $originalRequest")
  }

  def subscribedReliable(filterContext: FilterContext, originalRequest: FacadeRequest, lastRevisionId: Long, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(filterContext, originalRequest, event, lastRevisionId, subscriptionSyncTries)

    case RestartSubscription ⇒
      startSubscription(originalRequest, subscriptionSyncTries + 1)
  }

  def subscribedUnreliable(filterContext: FilterContext): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(filterContext, event)
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

    context.become(filtering(originalRequest, subscriptionSyncTries) orElse stopStartSubscription)

    val filterContextBefore = beforeFilterChain.createFilterContext(originalRequest, originalRequest)

    implicit val ec = executionContext
    beforeFilterChain.filterRequest(filterContextBefore, originalRequest) map { r ⇒
      val context2 = beforeFilterChain.createFilterContext(originalRequest, r)
      BeforeFilterComplete(context2, r)
    } recover handleFilterExceptions(filterContextBefore) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def continueSubscription(originalRequest: FacadeRequest,
                           filterContext: FilterContext,
                           facadeRequest: FacadeRequest,
                           subscriptionSyncTries: Int): Unit = {

    context.become(subscribing(originalRequest, filterContext, subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext

    processRequestWithRaml(filterContext, facadeRequest, 0) map { filteredRequest ⇒
      val correlationId = filteredRequest.headers.getOrElse(Header.CORRELATION_ID,
        filteredRequest.headers(Header.MESSAGE_ID)).head
      val subscriptionUri = getSubscriptionUri(filteredRequest)
      subscriptionManager.subscribe(self, subscriptionUri, correlationId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
      hyperbus <~ filteredRequest.copy(method = Method.GET).toDynamicRequest recover {
        handleHyperbusExceptions(filterContext)
      }  pipeTo self
    }
  }

  def processEventWhileSubscribing(filterContext: FilterContext, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing event while subscribing $event for ${filterContext.originalPath}")
    }

    event.headers.get(Header.REVISION) match {
      // reliable feed
      case Some(revision :: tail) ⇒
        log.debug(s"event $event is stashed because resource state is not fetched yet")
        stash()

      // unreliable feed
      case _ ⇒
        processUnreliableEvent(filterContext, event)
    }
  }

  def processResourceState(filterContext: FilterContext, resourceState: Response[DynamicBody], subscriptionSyncTries: Int) = {
    val facadeResponse = FacadeResponse(resourceState)
    if (log.isTraceEnabled) {
      log.trace(s"Processing resource state $resourceState for ${filterContext.originalPath}")
    }

    implicit val ec = executionContext
    ramlFilterChain.filterResponse(filterContext, facadeResponse) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(filterContext, filteredResponse) map { finalResponse ⇒
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
    } recover handleFilterExceptions(filterContext) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processUnreliableEvent(filterContext: FilterContext, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing unreliable event $event for ${filterContext.originalPath}")
    }
    implicit val ec = executionContext
    ramlFilterChain.filterEvent(filterContext, FacadeRequest(event)) flatMap { e ⇒
      afterFilterChain.filterEvent(filterContext, e) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } recover handleFilterExceptions(filterContext) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for $filterContext with filter response $response")
      }
    }
  }

  def processReliableEvent(filterContext: FilterContext,
                           originalRequest: FacadeRequest,
                           event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: tail) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing reliable event #$revisionId $event for ${filterContext.originalPath}")
        }

        if (revisionId == lastRevisionId + 1) {
          context.become(subscribedReliable(filterContext, originalRequest, lastRevisionId + 1, 0) orElse stopStartSubscription)

          implicit val ec = executionContext
          ramlFilterChain.filterEvent(filterContext, FacadeRequest(event)) flatMap { e ⇒
            afterFilterChain.filterEvent(filterContext, e) map { filteredRequest ⇒
              websocketWorker ! filteredRequest
            }
          } recover handleFilterExceptions(filterContext) { response ⇒
            if (log.isDebugEnabled) {
              log.debug(s"Event is discarded for $filterContext with filter response $response")
            }
          }
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          self ! RestartSubscription
          log.info(s"Subscription on ${filterContext.originalPath} lost events from $lastRevisionId to $revisionId. Restarting...")
        }
        // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: $filterContext")
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
case class BeforeFilterComplete(filterContext: FilterContext, facadeRequest: FacadeRequest)

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperbus,
    subscriptionManager))
}
