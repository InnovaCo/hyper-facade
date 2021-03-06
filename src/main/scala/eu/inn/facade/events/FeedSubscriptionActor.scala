package eu.inn.facade.events

import java.nio.BufferOverflowException
import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.pattern.pipe
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.workers.RequestProcessor
import eu.inn.facade.model.{FacadeRequest, FacadeResponse, _}
import eu.inn.facade.metrics.MetricKeys
import eu.inn.facade.raml.Method
import eu.inn.facade.utils.FutureUtils
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.transport.api.matchers.{RegexMatcher, TextMatcher}
import eu.inn.hyperbus.transport.api.uri._
import org.slf4j.LoggerFactory
import rx.lang.scala.{Observable, Observer}
import scaldi.Injector

import scala.concurrent.{ExecutionContext, Future}

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperbus: Hyperbus,
                            subscriptionManager: SubscriptionsManager)
                           (implicit val injector: Injector)
  extends Actor
    with RequestProcessor {

  val maxSubscriptionTries = config.getInt(FacadeConfigPaths.MAX_SUBSCRIPTION_TRIES)
  val maxStashedEventsCount = config.getInt(FacadeConfigPaths.FEED_MAX_STASHED_EVENTS_COUNT)

  val stashedBufferOverflow = metrics.meter(MetricKeys.WS_STASHED_EVENTS_BUFFER_OVERFLOW_COUNT)
  val stashedEventsCounter = metrics.counter(MetricKeys.WS_STASHED_EVENTS_COUNT)

  val log = LoggerFactory.getLogger(getClass)
  val executionContext = inject[ExecutionContext] // don't make this implicit

  def receive: Receive = stopStartSubscription orElse {
    case cwr: ContextWithRequest ⇒
      implicit val ec = executionContext
      processRequestToFacade(cwr) pipeTo websocketWorker
  }

  def filtering(subscriptionSyncTries: Int): Receive = {
    case BeforeFilterComplete(cwr) ⇒
      continueSubscription(cwr, subscriptionSyncTries)
  }

  def subscribing(cwr: ContextWithRequest, subscriptionSyncTries: Int, stashedEvents: Vector[StashedEvent]): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(cwr, event, subscriptionSyncTries, stashedEvents)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(cwr, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      Observable[DynamicRequest] { subscriber ⇒
        if (stashedEvents.isEmpty) {
          context.become(subscribedReliable(cwr, lastRevision, subscriptionSyncTries, subscriber) orElse stopStartSubscription)
      } else {
          context.become(waitForUnstash(cwr, Some(lastRevision), Some(subscriptionSyncTries), stashedEvents.tail, subscriber) orElse stopStartSubscription)
          if (log.isDebugEnabled) {
            log.debug(s"Reliable subscription will be started for ${cwr.context} with revision $lastRevision after unstashing of all events")
          }
          self ! stashedEvents.head
          stashedEventsCounter.dec()
        }
      } onBackpressureBuffer(maxStashedEventsCount) subscribe(eventsObserver(cwr))

    case BecomeUnreliable ⇒
      if (stashedEvents.isEmpty) {
        context.become(subscribedUnreliable(cwr) orElse stopStartSubscription)
      } else {
        Observable[DynamicRequest] { subscriber ⇒
          context.become(waitForUnstash(cwr, None, None, stashedEvents.tail, subscriber) orElse stopStartSubscription)
          if (log.isDebugEnabled) {
            log.debug(s"Unreliable subscription will be started for ${cwr.context} after unstashing of all events")
          }
          self ! stashedEvents.head
          stashedEventsCounter.dec()
        } onBackpressureBuffer(maxStashedEventsCount) subscribe(eventsObserver(cwr))
      }
    }

  def waitForUnstash(cwr: ContextWithRequest,
                     lastRevision: Option[Long],
                     subscriptionSyncTries: Option[Int],
                     stashedEvents: Vector[StashedEvent],
                     subscriber: Observer[DynamicRequest]): Receive = {
    case event: DynamicRequest ⇒
      if (stashedEvents.length > maxStashedEventsCount) {
        log.info(s"Stashed events buffer overflow while unstashing, seems that producer is faster than consumer. ${self.path.name} is resubscribing on ${cwr.context.pathAndQuery}...")
        stashedBufferOverflow.mark()
        stashedEventsCounter.dec(stashedEvents.length)
        continueSubscription(cwr, subscriptionSyncTries.getOrElse(1) + 1)
      } else {
        context.become(waitForUnstash(cwr, lastRevision, subscriptionSyncTries, stashedEvents :+ StashedEvent(event), subscriber))
        stashedEventsCounter.inc()
      }

    case StashedEvent(event) ⇒
      lastRevision match {
        case Some(revision) ⇒
          processReliableEventWhileUnstashing(cwr, event, revision, subscriptionSyncTries.getOrElse(1), stashedEvents, subscriber)
        case None ⇒
          processUnreliableEventWhileUnstashing(cwr, event, stashedEvents, subscriber)
      }
  }

  def subscribedReliable(cwr: ContextWithRequest, lastRevisionId: Long, subscriptionSyncTries: Int, subscriber: Observer[DynamicRequest]): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(cwr, event, lastRevisionId, subscriptionSyncTries, subscriber)
  }

  def subscribedUnreliable(cwr: ContextWithRequest): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(cwr, event)
  }

  def stopStartSubscription: Receive = {
    case cwr @ ContextWithRequest(_, _, FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _)) ⇒
      startSubscription(cwr, 0)

    case ContextWithRequest(_, _, FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _)) ⇒
      context.stop(self)
  }

  def startSubscription(cwr: ContextWithRequest, subscriptionSyncTries: Int): Unit = {
    if (log.isDebugEnabled()) {
      log.debug(s"Starting subscription #$subscriptionSyncTries for ${cwr.request.uri}")
    }
    if (subscriptionSyncTries > maxSubscriptionTries) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxSubscriptionTries) for ${cwr.request}")
      context.stop(self)
    }
    subscriptionManager.off(self)

    context.become(filtering(subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext
    beforeFilterChain.filterRequest(cwr) map { unpreparedContextWithRequest ⇒
      val cwrBeforeRaml = prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest)
      BeforeFilterComplete(cwrBeforeRaml)
    } recover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def continueSubscription(cwr: ContextWithRequest,
                           subscriptionSyncTries: Int): Unit = {

    context.become(subscribing(cwr, subscriptionSyncTries, Vector.empty) orElse stopStartSubscription)

    implicit val ec = executionContext

    val trackRequestTime = metrics.timer(MetricKeys.REQUEST_PROCESS_TIME).time()
    processRequestWithRaml(cwr) flatMap { cwrRaml ⇒
      val correlationId = cwrRaml.request.headers.getOrElse(Header.CORRELATION_ID,
        cwrRaml.request.headers(Header.MESSAGE_ID)).head
      val subscriptionUri = getSubscriptionUri(cwrRaml.request)
      subscriptionManager.off(self)
      subscriptionManager.subscribe(self, subscriptionUri, correlationId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
      hyperbus <~ cwrRaml.request.copy(method = Method.GET).toDynamicRequest
    } recover {
      handleHyperbusExceptions(cwr)
    } andThen { case _ ⇒
      trackRequestTime.stop
    } pipeTo self
  }

  def processEventWhileSubscribing(cwr: ContextWithRequest,
                                   event: DynamicRequest,
                                   subscriptionSyncTries: Int,
                                   stashedEvents: Vector[StashedEvent]): Unit = {
    if (log.isDebugEnabled()) {
      log.debug(s"event $event is stashed because resource state is not fetched yet")
    }

    if (stashedEvents.length > maxStashedEventsCount) {
      log.info(s"Stashed events buffer overflow while fetching resource state. ${self.path.name} is resubscribing on ${cwr.context.pathAndQuery}...")
      stashedBufferOverflow.mark()
      stashedEventsCounter.dec(stashedEvents.length)
      continueSubscription(cwr, subscriptionSyncTries + 1)
    } else {
      context.become(subscribing(cwr, subscriptionSyncTries, stashedEvents :+ StashedEvent(event)))
      stashedEventsCounter.inc()}
  }

  def processResourceState(cwr: ContextWithRequest, resourceState: Response[DynamicBody], subscriptionSyncTries: Int) = {
    val facadeResponse = FacadeResponse(resourceState)
    if (log.isDebugEnabled) {
      log.debug(s"Processing resource state $resourceState for ${cwr.context.pathAndQuery}")
    }

    implicit val ec = executionContext
    FutureUtils.chain(facadeResponse, cwr.stages.map { _ ⇒
      ramlFilterChain.filterResponse(cwr, _ : FacadeResponse)
    }) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(cwr, filteredResponse) map { finalResponse ⇒
        websocketWorker ! finalResponse
        if (finalResponse.status > 399) { // failed
          PoisonPill
        }
        else {
          finalResponse.headers.get(FacadeHeaders.CLIENT_REVISION) match {
            // reliable feed
            case Some(revision :: _) ⇒
              BecomeReliable(revision.toLong)

            // unreliable feed
            case _ ⇒
              BecomeUnreliable
          }
        }
      }
    } recover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processUnreliableEventWhileUnstashing(cwr: ContextWithRequest,
                                            event: DynamicRequest,
                                            stashedEvents: Vector[StashedEvent],
                                            subscriber: Observer[DynamicRequest]): Unit = {
    if (log.isDebugEnabled) {
      log.debug(s"Processing stashed unreliable event $event for ${cwr.context.pathAndQuery}")
    }
    if (stashedEvents.isEmpty) {
      context.become(subscribedUnreliable(cwr))
      if (log.isDebugEnabled) {
        log.debug(s"Unstashing completed for ${self.path.name}")
      }
    } else {
      context.become(waitForUnstash(cwr, None, None, stashedEvents.tail, subscriber) orElse stopStartSubscription)
      self ! stashedEvents.head
      stashedEventsCounter.dec()
    }
    subscriber.onNext(event)
  }

  def processUnreliableEvent(cwr: ContextWithRequest,
                             event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing unreliable event $event for ${cwr.context.pathAndQuery}")
    }
    implicit val ec = executionContext

    FutureUtils.chain(FacadeRequest(event), cwr.stages.map { _ ⇒
      ramlFilterChain.filterEvent(cwr, _ : FacadeRequest)
    }) flatMap { e ⇒
      afterFilterChain.filterEvent(cwr, e) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } recover handleFilterExceptions(cwr) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
      }
    }
  }

  def processReliableEventWhileUnstashing(cwr: ContextWithRequest,
                                          event: DynamicRequest,
                                          lastRevisionId: Long,
                                          subscriptionSyncTries: Int,
                                          stashedEvents: Vector[StashedEvent],
                                          subscriber: Observer[DynamicRequest]): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: _) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing stashed reliable event #$revisionId $event for ${cwr.context.pathAndQuery}")
        }

        if (revisionId == lastRevisionId + 1) {
          if (stashedEvents.isEmpty) {
            context.become(subscribedReliable(cwr, revisionId, subscriptionSyncTries, subscriber) orElse stopStartSubscription)
            if (log.isDebugEnabled) {
              log.debug(s"Unstashing completed for ${self.path.name}")
            }
          } else {
            context.become(waitForUnstash(cwr, Some(revisionId), Some(subscriptionSyncTries), stashedEvents.tail, subscriber) orElse stopStartSubscription)
            stashedEventsCounter.dec()
            self ! stashedEvents.head
          }
          subscriber.onNext(event)
        }
        else if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          log.info(s"Subscription on ${cwr.context.pathAndQuery} lost events from $lastRevisionId to $revisionId. Restarting subscription for ${self.path.name}...")
          stashedEventsCounter.dec(stashedEvents.length)
          continueSubscription(cwr, subscriptionSyncTries + 1)
        }
        else {  // if revisionId <= lastRevisionId -- just ignore this event and process next one
          if (stashedEvents.isEmpty) {
            context.become(subscribedReliable(cwr, lastRevisionId, subscriptionSyncTries, subscriber) orElse stopStartSubscription)
            if (log.isDebugEnabled) {
              log.debug(s"Unstashing completed for ${self.path.name}")
            }
          } else {
            context.become(waitForUnstash(cwr, Some(lastRevisionId), Some(subscriptionSyncTries), stashedEvents.tail, subscriber) orElse stopStartSubscription)
            self ! stashedEvents.head
            stashedEventsCounter.dec()
          }
        }

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: ${cwr.context}")
    }
  }

  def processReliableEvent(cwr: ContextWithRequest,
                           event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int,
                           subscriber: Observer[DynamicRequest]): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: _) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing reliable event #$revisionId $event for ${cwr.context.pathAndQuery}")
        }

        if (revisionId == lastRevisionId + 1) {
          subscriber.onNext(event)
          context.become(subscribedReliable(cwr, lastRevisionId + 1, 0, subscriber) orElse stopStartSubscription)
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          log.info(s"Subscription on ${cwr.context.pathAndQuery} lost events from $lastRevisionId to $revisionId. Restarting subscription for ${self.path.name}...")
          continueSubscription(cwr, subscriptionSyncTries + 1)
        }
        // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: ${cwr.context}")
    }
  }

  //  todo: this method seems hacky
  //  in this case we allow regular expression in URL
  def getSubscriptionUri(filteredRequest: FacadeRequest): Uri = {
    val uri = filteredRequest.uri
    val newArgs: Map[String, TextMatcher] = UriParser.tokens(uri.pattern.specific).flatMap {
      case ParameterToken(name, PathMatchType) ⇒
        Some(name → RegexMatcher(uri.args(name).specific + "/.*"))

      case ParameterToken(name, RegularMatchType) ⇒
        Some(name → uri.args(name))

      case _ ⇒ None
    }.toMap
    Uri(uri.pattern, newArgs)
  }

  def eventsObserver(cwr: ContextWithRequest): Observer[DynamicRequest] = {
    implicit val ec = executionContext
    new Observer[DynamicRequest] {
      val currentFilteringFuture = new AtomicReference[Option[Future[Unit]]](None)
      override def onNext(event: DynamicRequest): Unit = {
        val filteringFuture = FutureUtils.chain(FacadeRequest(event), cwr.stages.map { _ ⇒
          ramlFilterChain.filterEvent(cwr, _: FacadeRequest)
        }) flatMap { e ⇒
          afterFilterChain.filterEvent(cwr, e)
        }
        if (currentFilteringFuture.get().isEmpty) {
          val newCurrentFilteringFuture = filteringFuture map { filteredRequest ⇒
            websocketWorker ! filteredRequest
          } recover handleFilterExceptions(cwr) { response ⇒
             if (log.isDebugEnabled) {
               log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
             }
           }
          currentFilteringFuture.set(Some(newCurrentFilteringFuture))
        } else {
          val newCurrentFilteringFuture = currentFilteringFuture.get().get andThen {
            case _ ⇒
              filteringFuture map { filteredRequest ⇒
                websocketWorker ! filteredRequest
              } recover handleFilterExceptions(cwr) { response ⇒
                if (log.isDebugEnabled) {
                  log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
                }
              }
          }
          currentFilteringFuture.set(Some(newCurrentFilteringFuture))
        }
      }

      override def onError(error: Throwable): Unit = {
        error match {
          case _ : BufferOverflowException ⇒
            log.error(s"Backpressure overflow. Restarting...")
            context.stop(self)

          case other ⇒
            log.error(s"Error has occured on event processing. Restarting... $other")
            context.stop(self)
        }
      }
    }
  }
}

case class BecomeReliable(lastRevision: Long)
case object BecomeUnreliable
case class BeforeFilterComplete(cwr: ContextWithRequest)
case class StashedEvent(event: DynamicRequest)

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperbus,
    subscriptionManager))
}
