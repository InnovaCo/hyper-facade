package eu.inn.facade.events

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import com.typesafe.config.Config
import eu.inn.facade.HyperbusFactory
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model.{DynamicRequest, Header, Headers}
import eu.inn.hyperbus.transport.api.Subscription
import eu.inn.hyperbus.transport.api.matchers.{RegexMatcher, RequestMatcher}
import eu.inn.hyperbus.transport.api.uri._
import org.slf4j.LoggerFactory
import rx.lang.scala.Observer
import scaldi.{Injectable, Injector}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionsManager(implicit inj: Injector) extends Injectable {

  val hyperbus = inject[Hyperbus]
  val log = LoggerFactory.getLogger(SubscriptionsManager.this.getClass.getName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val executionContext = inject[ExecutionContext]
  val watchRef = actorSystem.actorOf(Props(new SubscriptionWatch(this)))

  def subscribe(clientActorRef: ActorRef, uri: Uri, correlationId: String): Unit =
    subscriptionManager.subscribe(clientActorRef, uri, correlationId)
  def off(clientActorRef: ActorRef) = subscriptionManager.off(clientActorRef)
  private val subscriptionManager = new Manager

  class Manager {
    val groupName = HyperbusFactory.defaultHyperbusGroup(inject[Config])
    val groupSubscriptions = scala.collection.mutable.Map[Uri,GroupSubscription]()
    val groupSubscriptionById = TrieMap[ActorRef, Uri]()

    case class ClientSubscriptionData(clientActorRef: ActorRef, uri: Uri, correlationId: String)

    class GroupSubscription(groupUri: Uri, initialSubscription: ClientSubscriptionData) {
      val clientSubscriptions = new ConcurrentLinkedQueue[ClientSubscriptionData]()
      var hyperbusSubscription: Option[Subscription] = None
      addClient(initialSubscription)

      val methodFilter = Map(Header.METHOD → RegexMatcher("^feed:.*$"))
      val observer = new Observer[DynamicRequest] {
        override def onNext(eventRequest: DynamicRequest): Unit = {
          log.debug(s"Event received ($groupName): $eventRequest")
          import scala.collection.JavaConversions._
          for (consumer: ClientSubscriptionData ← clientSubscriptions) {
            try {
              val matched = consumer.uri.matchUri(eventRequest.uri)
              log.debug(s"Event #(${eventRequest.messageId}) ${if (matched) "forwarded" else "NOT matched"} to ${consumer.clientActorRef}/${consumer.correlationId}")
              if (matched) {
                val request = eventRequest.copy(
                  headers = Headers.plain(eventRequest.headers + (Header.CORRELATION_ID → Seq(consumer.correlationId)))
                )
                consumer.clientActorRef ! request
              }
            }
            catch {
              case t: Throwable ⇒
                log.error("Can't forward subscription event", t)
            }
          }
        }
      }
      hyperbus.onEvent(RequestMatcher(Some(groupUri), methodFilter), groupName, observer) onSuccess {
        case subscription: Subscription ⇒ hyperbusSubscription = Some(subscription)
      }

      def addClient(subscription: ClientSubscriptionData) = clientSubscriptions.add(subscription)
      def removeClient(clientActorRef: ActorRef): Boolean = {
        import scala.collection.JavaConversions._
        for (consumer: ClientSubscriptionData ← clientSubscriptions) {
          if (consumer.clientActorRef == clientActorRef)
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

    def subscribe(clientActorRef: ActorRef, uri: Uri, correlationId: String): Unit = {
      watchRef ! NewSubscriber(clientActorRef)
      val subscriptionData = ClientSubscriptionData(clientActorRef, uri, correlationId)
      val groupUri = uri
      groupSubscriptionById += clientActorRef → groupUri
      groupSubscriptions.synchronized {
        groupSubscriptions.get(groupUri).map { list ⇒
          list.addClient(subscriptionData)
        } getOrElse {
          val groupSubscription = new GroupSubscription(groupUri, subscriptionData)
          groupSubscriptions += groupUri → groupSubscription
        }
      }
    }

    def off(clientActorRef: ActorRef) = {
      groupSubscriptionById.get(clientActorRef).foreach { groupUri ⇒
        groupSubscriptionById -= clientActorRef
        groupSubscriptions.synchronized {
          groupSubscriptions.get(groupUri).foreach { groupSubscription ⇒
            if (groupSubscription.removeClient(clientActorRef)) {
              groupSubscription.off()
              groupSubscriptions -= groupUri
            }
          }
        }
      }
    }
  }
}

case class NewSubscriber(actorRef: ActorRef)

class SubscriptionWatch(subscriptionManager: SubscriptionsManager) extends Actor with ActorLogging {
  override def receive: Receive = {
    case NewSubscriber(actorRef) ⇒
      log.debug(s"Watching new subscriber $actorRef")
      context.watch(actorRef)

    case Terminated(actorRef) ⇒
      log.debug(s"Actor $actorRef is died. Terminating subscription")
      subscriptionManager.off(actorRef)
  }
}
