package eu.inn.facade.events

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.Config
import eu.inn.facade.HyperbusFactory
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model.{DynamicRequest, Header, Headers}
import eu.inn.hyperbus.transport.api.Subscription
import eu.inn.hyperbus.transport.api.matchers.{RegexMatcher, RequestMatcher}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionsManager(implicit inj: Injector) extends Injectable {

  val hyperbus = inject[Hyperbus]
  val log = LoggerFactory.getLogger(SubscriptionsManager.this.getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  def subscribe(uri: Uri, clientActor: ActorRef, correlationId: String): String =
    subscriptionManager.subscribe(uri, clientActor, correlationId)

  def off(clientActor: ActorRef, subscriptionId: Option[String]) = subscriptionManager.off(clientActor, subscriptionId)

  private val subscriptionManager = new Manager

  class Manager {
    val groupName = HyperbusFactory.defaultHyperbusGroup(inject[Config])
    val idCounter = new AtomicLong(0)
    val groupSubscriptions = scala.collection.mutable.Map[Uri, GroupSubscription]()
    val groupSubscriptionById = TrieMap[ActorRef, Map[String, Uri]]()

    case class ClientSubscriptionData(subscriptionId: String, uri: Uri, clientActor: ActorRef, correlationId: String)

    class GroupSubscription(groupUri: Uri, initialSubscription: ClientSubscriptionData) {
      val clientSubscriptions = new ConcurrentLinkedQueue[ClientSubscriptionData]()
      var hyperbusSubscription: Option[Subscription] = None
      addClient(initialSubscription)

      val methodFilter = Map(Header.METHOD → RegexMatcher("^feed:.*$"))
      hyperbus.onEvent(RequestMatcher(Some(groupUri), methodFilter), groupName) { eventRequest: DynamicRequest ⇒
        Future {
          log.debug(s"Event received ($groupName): $eventRequest")
          import scala.collection.JavaConversions._
          for (consumer: ClientSubscriptionData ← clientSubscriptions) {
            try {
              val matched = consumer.uri.matchUri(eventRequest.uri)
              log.debug(s"Event #(${eventRequest.messageId}) ${if (matched) "forwarded" else "NOT matched"} to ${consumer.clientActor}/${consumer.correlationId}")
              if (matched) {
                val request = eventRequest.copy(
                  headers = Headers.plain(eventRequest.headers + (Header.CORRELATION_ID → Seq(consumer.correlationId)))
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
        case subscription: Subscription ⇒ hyperbusSubscription = Some(subscription)
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
        hyperbusSubscription match {
          case Some(subscription) ⇒ hyperbus.off(subscription)
          case None ⇒ log.warn("You cannot unsubscribe because you are not subscribed yet!")
        }
      }
    }

    def subscribe(uri: Uri, clientActor: ActorRef, correlationId: String): String = {
      val subscriptionId = idCounter.incrementAndGet().toHexString
      val subscriptionData = ClientSubscriptionData(subscriptionId, uri, clientActor, correlationId)
      val groupUri = uri
      groupSubscriptionById.synchronized {
        groupSubscriptionById.get(clientActor) match {
          case Some(oldClientSubscriptions) ⇒ groupSubscriptionById.update(clientActor, oldClientSubscriptions + (subscriptionId → groupUri))
          case None ⇒ groupSubscriptionById += clientActor → Map(subscriptionId → groupUri)
        }
      }
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

    def off(clientActor: ActorRef, subscriptionId: Option[String]) = {
      groupSubscriptionById.get(clientActor).foreach { clientSubscriptions ⇒
        subscriptionId match {
          // Certain subscription should be removed
          case Some(subscrIdToBeRemoved) ⇒ clientSubscriptions foreach {
            case (clientSubscriptionId, groupUri) if clientSubscriptionId == subscrIdToBeRemoved ⇒
              groupSubscriptionById.synchronized {
                val oldClientSubscriptions = groupSubscriptionById(clientActor)
                groupSubscriptionById.update(clientActor, oldClientSubscriptions - clientSubscriptionId)
                removeGroupSubscription(clientSubscriptionId, groupUri)
              }
          }

          // All subscriptions of client actor should be removed
          case None ⇒
            groupSubscriptionById.synchronized {
              groupSubscriptionById -= clientActor
              clientSubscriptions foreach {
                case (clientSubscriptionId, groupUri) ⇒ removeGroupSubscription(clientSubscriptionId, groupUri)
              }
            }
        }
      }
    }

    def removeGroupSubscription(clientSubscriptionId: String, groupUri: Uri): Unit = {
      groupSubscriptions.get(groupUri).foreach { groupSubscription ⇒
        if (groupSubscription.removeClient(clientSubscriptionId)) {
          groupSubscription.off()
          groupSubscriptions -= groupUri
        }
      }
    }
  }
}
