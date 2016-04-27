package eu.inn.facade.events

import akka.actor._
import akka.pattern.pipe
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.http.RequestProcessor
import eu.inn.facade.model.{FacadeResponse, _}
import eu.inn.facade.metrics.MetricKeys
import eu.inn.facade.raml.Method
import eu.inn.facade.utils.FutureUtils
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

  val maxSubscriptionTries = config.getInt(FacadeConfigPaths.MAX_SUBSCRIPTION_TRIES)
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

  def subscribing(cwr: ContextWithRequest, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(cwr, event)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(cwr, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      context.become(subscribedReliable(cwr, lastRevision, subscriptionSyncTries) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Reliable subscription started for ${cwr.context} with revision $lastRevision")

    case BecomeUnreliable ⇒
      context.become(subscribedUnreliable(cwr) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Unreliable subscription started for ${cwr.context}")
  }

  def subscribedReliable(cwr: ContextWithRequest, lastRevisionId: Long, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(cwr, event, lastRevisionId, subscriptionSyncTries)

    case RestartSubscription ⇒
      continueSubscription(cwr, subscriptionSyncTries + 1)
  }

  def subscribedUnreliable(cwr: ContextWithRequest): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(cwr, event)
  }

  def stopStartSubscription: Receive = {
    case cwr @ ContextWithRequest(_, _, request @ FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _)) ⇒
      startSubscription(cwr, 0)

    case ContextWithRequest(_, _, FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _)) ⇒
      context.stop(self)
  }

  def startSubscription(cwr: ContextWithRequest, subscriptionSyncTries: Int): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Starting subscription #$subscriptionSyncTries for ${cwr.request.uri}")
    }
    if (subscriptionSyncTries > maxSubscriptionTries) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxSubscriptionTries) for ${cwr.request}")
      context.stop(self)
    }
    subscriptionManager.off(self)

    context.become(filtering(subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext
    beforeFilterChain.filterRequest(cwr.context, cwr.request) map { unpreparedRequest ⇒
      val cwrBeforeRaml = prepareContextAndRequestBeforeRaml(cwr, unpreparedRequest)
      BeforeFilterComplete(cwrBeforeRaml)
    } recover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def continueSubscription(cwr: ContextWithRequest,
                           subscriptionSyncTries: Int): Unit = {

    context.become(subscribing(cwr, subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext

    val trackRequestTime = metrics.timer(MetricKeys.REQUEST_PROCESS_TIME).time()
    processRequestWithRaml(cwr) flatMap { cwrRaml ⇒
      val correlationId = cwrRaml.request.headers.getOrElse(Header.CORRELATION_ID,
        cwrRaml.request.headers(Header.MESSAGE_ID)).head
      val subscriptionUri = getSubscriptionUri(cwrRaml.request)
      subscriptionManager.subscribe(self, subscriptionUri, correlationId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
      hyperbus <~ cwrRaml.request.copy(method = Method.GET).toDynamicRequest
    } recover {
      handleHyperbusExceptions(cwr)
    } andThen { case _ ⇒
      trackRequestTime.stop
    } pipeTo self
  }

  def processEventWhileSubscribing(cwr: ContextWithRequest, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing event while subscribing $event for ${cwr.context.pathAndQuery}")
    }

    event.headers.get(Header.REVISION) match {
      // reliable feed
      case Some(revision :: tail) ⇒
        log.debug(s"event $event is stashed because resource state is not fetched yet")
        stash()

      // unreliable feed
      case _ ⇒
        processUnreliableEvent(cwr, event)
    }
  }

  def processResourceState(cwr: ContextWithRequest, resourceState: Response[DynamicBody], subscriptionSyncTries: Int) = {
    val facadeResponse = FacadeResponse(resourceState)
    if (log.isTraceEnabled) {
      log.trace(s"Processing resource state $resourceState for ${cwr.context.pathAndQuery}")
    }

    implicit val ec = executionContext
    FutureUtils.chain(facadeResponse, cwr.stages.map { stage ⇒
      ramlFilterChain.filterResponse(cwr.context, _ : FacadeResponse)
    }) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(cwr.context, filteredResponse) map { finalResponse ⇒
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
    } recover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processUnreliableEvent(cwr: ContextWithRequest, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing unreliable event $event for ${cwr.context.pathAndQuery}")
    }
    implicit val ec = executionContext

    FutureUtils.chain(FacadeRequest(event), cwr.stages.map { stage ⇒
      ramlFilterChain.filterEvent(cwr.context, _ : FacadeRequest)
    }) flatMap { e ⇒
      afterFilterChain.filterEvent(cwr.context, e) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } recover handleFilterExceptions(cwr) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
      }
    }
  }

  def processReliableEvent(cwr: ContextWithRequest,
                           event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: tail) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing reliable event #$revisionId $event for ${cwr.context.pathAndQuery}")
        }

        if (revisionId == lastRevisionId + 1) {
          context.become(subscribedReliable(cwr, lastRevisionId + 1, 0) orElse stopStartSubscription)

          implicit val ec = executionContext

          FutureUtils.chain(FacadeRequest(event), cwr.stages.map { stage ⇒
            ramlFilterChain.filterEvent(cwr.context, _ : FacadeRequest)
          }) flatMap { e ⇒
            afterFilterChain.filterEvent(cwr.context, e) map { filteredRequest ⇒
              websocketWorker ! filteredRequest
            }
          } recover handleFilterExceptions(cwr) { response ⇒
            if (log.isDebugEnabled) {
              log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
            }
          }
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          self ! RestartSubscription
          log.info(s"Subscription on ${cwr.context.pathAndQuery} lost events from $lastRevisionId to $revisionId. Restarting...")
        }
        // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: ${cwr.context}")
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
case class BeforeFilterComplete(cwr: ContextWithRequest)

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperbus,
    subscriptionManager))
}
