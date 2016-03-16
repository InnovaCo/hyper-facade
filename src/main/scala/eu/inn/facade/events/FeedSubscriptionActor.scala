package eu.inn.facade.events

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.pattern.pipe
import com.typesafe.config.Config
import eu.inn.facade.filter.chain.{FilterChains, FilterChainFactory}
import eu.inn.facade.http.RequestMapper
import eu.inn.facade.model
import eu.inn.facade.model._
import eu.inn.facade.raml.{Method, RamlConfig}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import scala.util.control.NonFatal

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperBus: HyperBus,
                            subscriptionManager: SubscriptionsManager)
                           (implicit inj: Injector)
  extends Actor
  with ActorLogging
  with Injectable {

  val filterChains = inject[FilterChains]
  val ramlConfig = inject[RamlConfig]
  //val pendingEvents = new ConcurrentLinkedQueue[FacadeRequest]
  val maxResubscriptionsCount = inject[Config].getInt("inn.facade.maxResubscriptionsCount")
  //val feedState = new AtomicReference[FeedState](FeedState())

  //val subscriptionId = new AtomicReference[Option[String]](None)
  //val initialClientRequest = new AtomicReference[Option[FacadeRequest]](None)

  def receive: Receive = {
    case requst@FacadeRequest(_, ClientSpecificMethod.UNSUBSCRIBE, _, _) ⇒
      context.stop(self)

    case requst@FacadeRequest(_, ClientSpecificMethod.SUBSCRIBE, _, _) ⇒
      startSubscription(request)

    case requst : FacadeRequest ⇒
      executeRequest(request)

    case other ⇒
      log.error(s"Invalid request received on $self: $other")
      context.stop(self)
  }

  def executeRequest(request: FacadeRequest): Unit = {
    filterChains.filterRequest(request) flatMap { filteredRequest ⇒
      implicit val mvx = serverMvx(filteredRequest)
      hyperBus <~ filteredRequest.toDynamicRequest flatMap { response ⇒
        filterChains.filterResponse(request, FacadeResponse(response))
      }
    } recover {
      handleErrors(request)
    } pipeTo websocketWorker
  }

  def startSubscription(request: FacadeRequest): Unit = {
    filterChains.filterRequest(request) map { filteredRequest ⇒
      implicit val mvx = serverMvx(filteredRequest)
      val subscriptionId = subscriptionManager.subscribe(request.uri, self, request.correlationId)
      context.become(subscribing(subscriptionId, request, Seq.empty))

    } recover {
      handleErrors(request)
    } pipeTo self
  }

  def handleErrors(request: FacadeRequest): PartialFunction[Throwable, Any] = {
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
  }

  def subscribing(subscriptionId: String, request: FacadeRequest, pendingEvents: Seq[FacadeRequest]): Receive = {

  }

  def subscribed(subscriptionId: String, request: FacadeRequest, fedState: FeedState): Receive = {
  }




  {
      request.headers.get(Header.METHOD) match {
        case Some(Seq(_)) ⇒
          initialClientRequest.set(Some(request))
          filterAndSubscribe(request)

        case Some(Seq(ClientSpecificMethod.UNSUBSCRIBE)) ⇒
          context.stop(self)
      }

    case request: DynamicRequest ⇒
      processEvent(request)

    case other ⇒
      log.error(s"Invalid request received on $self: $other")
      context.stop(self)
  }

  override def postStop(): Unit = {
    unsubscribe()
  }

  def processEvent(event: DynamicRequest): Unit = {
    val facadeEvent = FacadeRequest(event)
    val feedStateSnapshot = feedState.get
    val resourceStateFetched = feedStateSnapshot.resourceStateFetched
    event.headers.get(Header.REVISION) match {
      case Some(revision) ⇒
        if (!resourceStateFetched || !pendingEvents.isEmpty) {
          pendingEvents.add(facadeEvent)
          log.info(s"event $event is enqueued because resource state is not fetched yet")
        }
        else {
          sendEvent(facadeEvent)
        }

      case None ⇒
        if (resourceStateFetched) {
          initialClientRequest.get foreach { request ⇒
            filterChains.filterEvent(request, facadeEvent).map(_.toDynamicRequest) pipeTo websocketWorker
          }
        } else {
          log.warning(s"event $event will be dropped because resource state is not fetched yet")
        }
    }
  }



  def executeRequest(request: FacadeRequest)
                    (implicit mvx: MessagingContextFactory): Future[Seq[FacadeMessage]] = {
    val feedStateSnapshot = feedState.get
    hyperBus <~ request.toDynamicRequest flatMap { response ⇒
      filterChains.filterResponse(request, FacadeResponse(response)) map { filteredResponse ⇒
        filteredResponse.headers.get(FacadeHeaders.CLIENT_REVISION_ID) match {
          case Some(revisionIdSeq) if revisionIdSeq.nonEmpty ⇒
            val revisionId = revisionIdSeq.head.toLong
            val resourceStateFetched = true
            val reliableFeed = true
            val resubscriptionCount = feedStateSnapshot.resubscriptionCount
            val updated = feedState.compareAndSet(feedStateSnapshot, FeedState(resourceStateFetched, reliableFeed, revisionId, resubscriptionCount))
            if (updated) {
              filteredResponse :+ sendQueuedEvents()
            }
            else {
              Seq.empty
            }

          case None ⇒
            val reliableFeed = false
            val resourceStateFetched = true
            val updated = feedState.compareAndSet(feedStateSnapshot, FeedState(resourceStateFetched, reliableFeed, 0L, 0))
            if (updated) {
              Seq(filteredResponse)
            }
            else {
              Seq.empty
            }
        }
      }
    }
  }

  def sendQueuedEvents(): Unit = {
    while (!pendingEvents.isEmpty) {
      sendEvent(pendingEvents.poll())
    }
  }

  private def sendEvent(event: FacadeRequest): Unit = {
    val feedStateSnapshot = feedState.get
    val revisionId = event.headers(Header.REVISION).head.toLong
    initialClientRequest.get foreach { request ⇒
      if (revisionId == feedStateSnapshot.lastRevisionId + 1)
        filterEvent(request, event) map {
          case (headers: TransitionalHeaders, body: DynamicBody) ⇒
            val lastRevisionId = revisionId
            val resubscriptionCount = feedStateSnapshot.resubscriptionCount
            val updated = feedState.compareAndSet(feedStateSnapshot, FeedState(true, true, lastRevisionId, resubscriptionCount))
            if (updated) websocketWorker ! RequestMapper.toDynamicRequest(headers, body)
        }
      else if (revisionId > feedStateSnapshot.lastRevisionId + 1) resubscribe(request)
      // if revisionId <= lastRevisionId -- just ignore this event
    }
  }

  private def resubscribe(request: DynamicRequest): Unit = {
    unsubscribe()
    val resubscriptionCount = feedState.get.lastRevisionId + 1
    if (resubscriptionCount > maxResubscriptionsCount)
      context.stop(self)
    val lastRevisionId = -1
    pendingEvents.clear()
    filterAndSubscribe(request)
  }

  def unsubscribe(): Unit = {
    subscriptionId.get.foreach(subscriptionManager.off)
    subscriptionId.set(None)
  }

  def subscribe(request: FacadeRequest): Unit = {
    val finalCorrelationId = RequestMapper.correlationId(request.headers)

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
