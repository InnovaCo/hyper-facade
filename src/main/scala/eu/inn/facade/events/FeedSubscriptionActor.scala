package eu.inn.facade.events

import akka.actor._
import akka.pattern.pipe
import eu.inn.facade.FacadeConfigPaths
import eu.inn.facade.http.RequestProcessor
import eu.inn.facade.model.{FacadeResponse, _}
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
    case fct: FCT ⇒
      implicit val ec = executionContext
      processRequestToFacade(fct) pipeTo websocketWorker
  }

  def filtering(subscriptionSyncTries: Int): Receive = {
    case BeforeFilterComplete(fct) ⇒
      continueSubscription(fct, subscriptionSyncTries)
  }

  def subscribing(fct: FCT, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(fct, event)

    case resourceState: Response[DynamicBody] @unchecked ⇒
      processResourceState(fct, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      context.become(subscribedReliable(fct, lastRevision, subscriptionSyncTries) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Reliable subscription started for ${fct.context} with revision $lastRevision")

    case BecomeUnreliable ⇒
      context.become(subscribedUnreliable(fct) orElse stopStartSubscription)
      unstashAll()
      log.debug(s"Unreliable subscription started for ${fct.context}")
  }

  def subscribedReliable(fct: FCT, lastRevisionId: Long, subscriptionSyncTries: Int): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(fct, event, lastRevisionId, subscriptionSyncTries)

    case RestartSubscription ⇒
      continueSubscription(fct, subscriptionSyncTries + 1)
  }

  def subscribedUnreliable(fct: FCT): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(fct, event)
  }

  def stopStartSubscription: Receive = {
    case fct @ FCT(_, _, request @ FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _)) ⇒
      startSubscription(fct, 0)

    case fct @ FCT(_, _, FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _)) ⇒
      context.stop(self)
  }

  def startSubscription(fct: FCT, subscriptionSyncTries: Int): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Starting subscription #$subscriptionSyncTries for ${fct.request.uri}")
    }
    if (subscriptionSyncTries > maxSubscriptionTries) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxSubscriptionTries) for ${fct.request}")
      context.stop(self)
    }
    subscriptionManager.off(self)

    context.become(filtering(subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext
    beforeFilterChain.filterRequest(fct.context, fct.request) map { unpreparedRequest ⇒
      val fctBeforeRaml = prepareContextAndRequestBeforeRaml(fct, unpreparedRequest)
      BeforeFilterComplete(fctBeforeRaml)
    } recover handleFilterExceptions(fct.context) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def continueSubscription(fctIn: FCT,
                           subscriptionSyncTries: Int): Unit = {

    context.become(subscribing(fctIn, subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = executionContext

    processRequestWithRaml(fctIn) flatMap { fct ⇒
      val correlationId = fct.request.headers.getOrElse(Header.CORRELATION_ID,
        fct.request.headers(Header.MESSAGE_ID)).head
      val subscriptionUri = getSubscriptionUri(fct.request)
      subscriptionManager.subscribe(self, subscriptionUri, correlationId)
      implicit val mvx = MessagingContextFactory.withCorrelationId(correlationId + self.path.toString) // todo: check what's here
      hyperbus <~ fct.request.copy(method = Method.GET).toDynamicRequest
    } recover {
      handleHyperbusExceptions(fctIn.context)
    } pipeTo self
  }

  def processEventWhileSubscribing(fct: FCT, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing event while subscribing $event for ${fct.context.pathAndQuery}")
    }

    event.headers.get(Header.REVISION) match {
      // reliable feed
      case Some(revision :: tail) ⇒
        log.debug(s"event $event is stashed because resource state is not fetched yet")
        stash()

      // unreliable feed
      case _ ⇒
        processUnreliableEvent(fct, event)
    }
  }

  def processResourceState(fct: FCT, resourceState: Response[DynamicBody], subscriptionSyncTries: Int) = {
    val facadeResponse = FacadeResponse(resourceState)
    if (log.isTraceEnabled) {
      log.trace(s"Processing resource state $resourceState for ${fct.context.pathAndQuery}")
    }

    implicit val ec = executionContext
    FutureUtils.chain(facadeResponse, fct.stages.map { stage ⇒
      ramlFilterChain.filterResponse(fct.context, _ : FacadeResponse)
    }) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(fct.context, filteredResponse) map { finalResponse ⇒
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
    } recover handleFilterExceptions(fct.context) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processUnreliableEvent(fct: FCT, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing unreliable event $event for ${fct.context.pathAndQuery}")
    }
    implicit val ec = executionContext

    FutureUtils.chain(FacadeRequest(event), fct.stages.map { stage ⇒
      ramlFilterChain.filterEvent(fct.context, _ : FacadeRequest)
    }) flatMap { e ⇒
      afterFilterChain.filterEvent(fct.context, e) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } recover handleFilterExceptions(fct.context) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for ${fct.context} with filter response $response")
      }
    }
  }

  def processReliableEvent(fct: FCT,
                           event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: tail) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing reliable event #$revisionId $event for ${fct.context.pathAndQuery}")
        }

        if (revisionId == lastRevisionId + 1) {
          context.become(subscribedReliable(fct, lastRevisionId + 1, 0) orElse stopStartSubscription)

          implicit val ec = executionContext

          FutureUtils.chain(FacadeRequest(event), fct.stages.map { stage ⇒
            ramlFilterChain.filterEvent(fct.context,  _ : FacadeRequest)
          }) flatMap { e ⇒
            afterFilterChain.filterEvent(fct.context, e) map { filteredRequest ⇒
              websocketWorker ! filteredRequest
            }
          } recover handleFilterExceptions(fct.context) { response ⇒
            if (log.isDebugEnabled) {
              log.debug(s"Event is discarded for ${fct.context} with filter response $response")
            }
          }
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          self ! RestartSubscription
          log.info(s"Subscription on ${fct.context.pathAndQuery} lost events from $lastRevisionId to $revisionId. Restarting...")
        }
        // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: ${fct.context}")
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
case class BeforeFilterComplete(fct: FCT)

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperbus,
    subscriptionManager))
}
