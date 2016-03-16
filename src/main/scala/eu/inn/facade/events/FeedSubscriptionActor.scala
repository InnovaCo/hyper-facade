package eu.inn.facade.events

import akka.actor._
import akka.pattern.pipe
import com.typesafe.config.Config
import eu.inn.facade.filter.chain.FilterChains
import eu.inn.facade.http.RequestMapper
import eu.inn.facade.model._
import eu.inn.facade.raml.{Method, RamlConfig}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperBus: HyperBus,
                            subscriptionManager: SubscriptionsManager)
                           (implicit inj: Injector)
  extends Actor
  with ActorLogging
  with Stash
  with Injectable {

  val filterChains = inject[FilterChains]
  val ramlConfig = inject[RamlConfig]
  val maxResubscriptionsCount = inject[Config].getInt("inn.facade.maxResubscriptionsCount")
  var subscriptionId: Option[String] = None

  def receive: Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case request @ FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _) ⇒
      startSubscription(request, 0)

    case request : FacadeRequest ⇒
      processRequest(request)

    case other ⇒
      log.error(s"Invalid request received on $self: $other")
      context.stop(self)
  }

  def subscribing(request: FacadeRequest, subscriptionSyncTries: Int): Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case event: DynamicRequest ⇒
      event.headers.get(Header.REVISION) match {
        // reliable feed
        case Some(revision :: tail) ⇒
          log.debug(s"event $event is stashed because resource state is not fetched yet")
          stash()

        // unreliable feed
        case _ ⇒
          processUnreliableEvent(request, event)
      }

    case resourceState: Response[DynamicBody] ⇒
      val facadeResponse = FacadeResponse(resourceState)
      filterChains.filterResponse(request, facadeResponse) map { filteredResponse ⇒
        websocketWorker ! filteredResponse
        filteredResponse.headers.get(Header.REVISION) match {
          // reliable feed
          case Some(revision :: tail) ⇒
            context.become(subscribedReliable(request, revision.toLong, subscriptionSyncTries))

          // unreliable feed
          case _ ⇒
            context.become(subscribedUnreliable(request))
        }
        unstashAll()
      } onFailure {
        case e: FilterInterruptException ⇒
          if (e.getCause != null) {
            log.error(e, s"Request execution interrupted: $request")
          }
          websocketWorker ! e.response
          context.stop(self)

        case NonFatal(e) ⇒
          val response = RequestMapper.exceptionToResponse(e)
          log.error(s"Service answered with error #${response.body.asInstanceOf[ErrorBody].errorId}. Stopping actor")
          websocketWorker ! FacadeResponse(response)
          context.stop(self)
      }
  }

  def subscribedReliable(request: FacadeRequest, lastRevisionId: Long, resubscriptionCount: Int): Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)
    case event: DynamicRequest ⇒
  }

  def subscribedUnreliable(request: FacadeRequest): Receive = {
    case FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)
    case event: DynamicRequest ⇒
      processUnreliableEvent(request, event)
  }

  def processRequest(request: FacadeRequest): Unit = {
    filterChains.filterRequest(request) flatMap { filteredRequest ⇒
      implicit val mvx = serverMvx(filteredRequest)
      hyperBus <~ filteredRequest.toDynamicRequest flatMap { response ⇒
        filterChains.filterResponse(request, FacadeResponse(response))
      }
    } recover {
      case e: FilterInterruptException ⇒
        if (e.getCause != null) {
          log.error(e, s"Request execution interrupted: $request")
        }
        e.response

      case NonFatal(e) ⇒
        val response = RequestMapper.exceptionToResponse(e)
        log.error(s"Service answered with error #${response.body.asInstanceOf[ErrorBody].errorId}. Stopping actor")
        self ! PoisonPill
        FacadeResponse(response)
    } pipeTo websocketWorker
  }

  def startSubscription(request: FacadeRequest, subscriptionSyncTries: Int): Unit = {
    if (subscriptionSyncTries > maxResubscriptionsCount) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxResubscriptionsCount) for $request")
      context.stop(self)
    }
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
    filterChains.filterRequest(request) map { filteredRequest ⇒
      implicit val mvx = serverMvx(filteredRequest)
      this.subscriptionId = Some(subscriptionManager.subscribe(request.uri, self, request.correlationId))
      context.become(subscribing(request, subscriptionSyncTries))
      hyperBus <~ request.copy(method = Method.GET).toDynamicRequest flatMap { response ⇒
        filterChains.filterResponse(request, FacadeResponse(response))
      }
    } pipeTo self
  }

  def processUnreliableEvent(request: FacadeRequest, event: DynamicRequest): Unit = {
    filterChains.filterEvent(request, FacadeRequest(event)) map { filteredRequest ⇒
      websocketWorker ! filteredRequest.toDynamicRequest
    } recover {
      case e: FilterInterruptException ⇒
        if (e.getCause != null) {
          log.error(e, s"Event response interrupted for request: $request")
        }
        else {
          log.debug(s"Event response were discarded $e for request: $request")
        }

      case NonFatal(e) ⇒
        log.error(e, s"Event filtering failed for request: $request")
    }
  }

  def processReliableEvent(request: FacadeRequest, event: DynamicRequest, lastRevisionId: Long, subscriptionSyncTries: Int): Unit = {
    filterChains.filterEvent(request, FacadeRequest(event)) map { filteredRequest ⇒
      val revisionId = filteredRequest.headers(Header.REVISION).head.toLong

      if (revisionId == lastRevisionId + 1) {
        websocketWorker ! filteredRequest.toDynamicRequest
      }
      else
      if (revisionId > lastRevisionId + 1) {
        // we lost some events, start from the beginning
        startSubscription(request, subscriptionSyncTries + 1)
      }
      // if revisionId <= lastRevisionId -- just ignore this event
    } recover {
      case e: FilterInterruptException ⇒
        if (e.getCause != null) {
          log.error(e, s"Event response interrupted for request: $request")
        }
        else {
          log.debug(s"Event response were discarded $e for request: $request")
        }

      case NonFatal(e) ⇒
        log.error(e, s"Event filtering failed for request: $request")
    }
  }

  override def postStop(): Unit = {
    subscriptionId.foreach(subscriptionManager.off)
    subscriptionId = None
  }

  def serverMvx(filteredRequest: FacadeRequest) = MessagingContextFactory.withCorrelationId(serverCorrelationId(filteredRequest))

  def serverCorrelationId(request: FacadeRequest): String = {
    request.correlationId + self.path
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
