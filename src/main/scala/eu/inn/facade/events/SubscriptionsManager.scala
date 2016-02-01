package eu.inn.facade.events

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.Config
import eu.inn.facade.HyperBusFactory
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.DynamicRequest
import eu.inn.hyperbus.model.standard.Method
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api.Topic
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionsManager(implicit inj: Injector) extends Injectable {

  val hyperBus = inject[HyperBus]
  val log = LoggerFactory.getLogger(SubscriptionsManager.this.getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]

  def subscribe(topicFilter: Topic, clientActor: ActorRef, correlationId: String): String =
    subscriptionManager.subscribe(topicFilter, clientActor, correlationId)
  def off(subscriptionId: String) = subscriptionManager.off(subscriptionId)
  private val subscriptionManager = new Manager

  class Manager {
    val groupName = HyperBusFactory.defaultHyperBusGroup(inject[Config])
    val idCounter = new AtomicLong(0)
    val groupSubscriptions = scala.collection.mutable.Map[Topic,GroupSubscription]()
    val groupSubscriptionById = TrieMap[String, Topic]()

    case class ClientSubscriptionData(subscriptionId: String, topicFilter: Topic, clientActor: ActorRef, correlationId: String)

    class GroupSubscription(groupTopic: Topic, initialSubscription: ClientSubscriptionData) {
      val clientSubscriptions = new ConcurrentLinkedQueue[ClientSubscriptionData]()
      addClient(initialSubscription)

      val hyperBusSubscriptionId = hyperBus.onEvent(groupTopic, Method.POST, None, groupName) { eventRequest: DynamicRequest ⇒
        Future{
          log.debug(s"Event received ($groupName): $eventRequest")
          import scala.collection.JavaConversions._
          for (consumer: ClientSubscriptionData ← clientSubscriptions) {
            try {
              val matched = consumer.topicFilter.matchTopic(eventRequest.topic)
              log.debug(s"Event #(${eventRequest.messageId}) ${if (matched) "forwarded" else "NOT matched"} to ${consumer.clientActor}/${consumer.correlationId}")
              if (matched) {
                val request = DynamicRequest(
                  RequestHeader(eventRequest.topic.url.specific,
                    eventRequest.method,
                    eventRequest.body.contentType,
                    eventRequest.messageId,
                    Some(consumer.correlationId)),
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
        hyperBus.off(hyperBusSubscriptionId)
      }
    }

    def subscribe(topicFilter: Topic, clientActor: ActorRef, correlationId: String): String = {
      val subscriptionId = idCounter.incrementAndGet().toHexString
      val subscriptionData = ClientSubscriptionData(subscriptionId, topicFilter, clientActor, correlationId)
      val groupTopic = topicFilter
      groupSubscriptionById += subscriptionId → groupTopic
      groupSubscriptions.synchronized {
        groupSubscriptions.get(groupTopic).map { list ⇒
          list.addClient(subscriptionData)
        } getOrElse {
          val groupSubscription = new GroupSubscription(groupTopic, subscriptionData)
          groupSubscriptions += groupTopic → groupSubscription
        }
      }
      subscriptionId
    }

    def off(subscriptionId: String) = {
      groupSubscriptionById.get(subscriptionId).foreach { groupTopic ⇒
        groupSubscriptionById -= subscriptionId
        groupSubscriptions.synchronized {
          groupSubscriptions.get(groupTopic).foreach { groupSubscription ⇒
            if (groupSubscription.removeClient(subscriptionId)) {
              groupSubscription.off()
              groupSubscriptions -= groupTopic
            }
          }
        }
      }
    }
  }
}