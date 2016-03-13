package eu.inn.facade.events

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.Config
import eu.inn.facade.HyperBusFactory
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.{DynamicRequest, Header}
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.Subscription
import eu.inn.hyperbus.transport.api.matchers.{RegexMatcher, RequestMatcher}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionsManager(implicit inj: Injector) extends Injectable {

  val hyperBus = inject[HyperBus]
  val log = LoggerFactory.getLogger(SubscriptionsManager.this.getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  def subscribe(uri: Uri, clientActor: ActorRef, correlationId: String): String =
    subscriptionManager.subscribe(uri, clientActor, correlationId)
  def off(subscriptionId: String) = subscriptionManager.off(subscriptionId)
  private val subscriptionManager = new Manager

  class Manager {
    val groupName = HyperBusFactory.defaultHyperBusGroup(inject[Config])
    val idCounter = new AtomicLong(0)
    val groupSubscriptions = scala.collection.mutable.Map[Uri,GroupSubscription]()
    val groupSubscriptionById = TrieMap[String, Uri]()

    case class ClientSubscriptionData(subscriptionId: String, uri: Uri, clientActor: ActorRef, correlationId: String)

    class GroupSubscription(groupUri: Uri, initialSubscription: ClientSubscriptionData) {
      val clientSubscriptions = new ConcurrentLinkedQueue[ClientSubscriptionData]()
      var hyperBusSubscription: Option[Subscription] = None
      addClient(initialSubscription)

      val methodFilter = Map(Header.METHOD → RegexMatcher("feed:.*"))
      hyperBus.onEvent(RequestMatcher(Some(groupUri), methodFilter), groupName) { eventRequest: DynamicRequest ⇒
        Future{
          log.debug(s"Event received ($groupName): $eventRequest")
          import scala.collection.JavaConversions._
          for (consumer: ClientSubscriptionData ← clientSubscriptions) {
            try {
              val matched = consumer.uri.matchUri(eventRequest.uri)
              log.debug(s"Event #(${eventRequest.messageId}) ${if (matched) "forwarded" else "NOT matched"} to ${consumer.clientActor}/${consumer.correlationId}")
              if (matched) {
                val request = DynamicRequest(
                  RequestHeader(
                    eventRequest.uri,
                    eventRequest.headers + (Header.CORRELATION_ID → Seq(consumer.correlationId))
                  ),
                  eventRequest.body
                )
                consumer.clientActor ! request
              }
            }
            catch {
              case t: Throwable ⇒
                log.error("Can't forward subscription event", t)
            }
          }
        }
      } onSuccess {
        case subscription: Subscription ⇒ hyperBusSubscription = Some(subscription)
      }

      def addClient(subscription: ClientSubscriptionData) = clientSubscriptions.add(subscription)
      def removeClient(subscriptionId: String): Boolean = {
        import scala.collection.JavaConversions._
        for (consumer: ClientSubscriptionData ← clientSubscriptions) {
          if (consumer.subscriptionId == subscriptionId)
            clientSubscriptions.remove(consumer)
        }
        clientSubscriptions.isEmpty
      }

      def off() = {
        hyperBusSubscription match {
          case Some(subscription) ⇒ hyperBus.off(subscription)
          case None ⇒ log.warn("You cannot unsubscribe because you are not subscribed yet!")
        }
      }
    }

    def subscribe(uri: Uri, clientActor: ActorRef, correlationId: String): String = {
      val subscriptionId = idCounter.incrementAndGet().toHexString
      val subscriptionData = ClientSubscriptionData(subscriptionId, uri, clientActor, correlationId)
      val groupUri = uri
      groupSubscriptionById += subscriptionId → groupUri
      groupSubscriptions.synchronized {
        groupSubscriptions.get(groupUri).map { list ⇒
          list.addClient(subscriptionData)
        } getOrElse {
          val groupSubscription = new GroupSubscription(groupUri, subscriptionData)
          groupSubscriptions += groupUri → groupSubscription
        }
      }
      subscriptionId
    }

    def off(subscriptionId: String) = {
      groupSubscriptionById.get(subscriptionId).foreach { groupUri ⇒
        groupSubscriptionById -= subscriptionId
        groupSubscriptions.synchronized {
          groupSubscriptions.get(groupUri).foreach { groupSubscription ⇒
            if (groupSubscription.removeClient(subscriptionId)) {
              groupSubscription.off()
              groupSubscriptions -= groupUri
            }
          }
        }
      }
    }
  }
}