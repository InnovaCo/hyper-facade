package eu.inn.facade.filter

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.filter.model.DynamicRequestHeaders._
import eu.inn.facade.filter.model.{Headers, InputFilter, OutputFilter}
import eu.inn.facade.http.RequestMapper._
import eu.inn.facade.http.{Connect, WsTestClient, WsTestWorker}
import eu.inn.hyperbus.model.standard.Method
import eu.inn.hyperbus.model.{DynamicBody, DynamicRequest}
import eu.inn.hyperbus.serialization.RequestHeader
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

import scala.concurrent.{Future, Promise}
import scala.util.Success

class WsFilterChainTest extends FreeSpec with Matchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  class TestInputFilter extends InputFilter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (requestHeaders.headers.nonEmpty) {
        val filteredHeaders = requestHeaders.headers.filterNot{ _._1 == CORRELATION_ID }
        Future(Headers(filteredHeaders, None), body)
      }
      else Future(requestHeaders withStatusCode Some(403), DynamicBody(Text("Forbidden")))
    }
  }

  class TestOutputFilter extends OutputFilter {
    override def apply(responseHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (responseHeaders.headers.nonEmpty) {
        val filteredHeaders = responseHeaders.headers.filterNot { _._1 == CORRELATION_ID }
        Future(Headers(filteredHeaders, None), body)
      }
      else Future(Headers(Map("x-http-header" → "Accept-Language"), Some(200)), null)
    }
  }

  "WsFilterChain " - {
    "websocket: applyInputFilters empty filterChain, non-empty headers" in {
      implicit val system = ActorSystem()

      val host = "localhost"
      val port = 12345
      val url = "/testFilterChain"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))
      val onClientUpgradePromise = Promise[Boolean]()

      val client = system.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = ()

        override def onUpgrade: Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val filteredDynamicRequestPromise = Promise[DynamicRequest]()
      def exposeDynamicRequest: (DynamicRequest ⇒ Unit) = { filteredDynamicRequest ⇒
        filteredDynamicRequestPromise.complete(Success(filteredDynamicRequest))
      }
      val server = system.actorOf(Props(wsWorker(FilterChain(), FilterChain(), exposeDynamicRequest)), "websocket-worker")
      try {
        val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
          case b: Http.Bound ⇒
            Future.successful(b)
        }
        whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
          client ! Connect()

          whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
            client ! DynamicRequest(RequestHeader("/test", Method.GET, None, "messageId", Some("correlationId")), DynamicBody(Text("haha")))

            whenReady(filteredDynamicRequestPromise.future, Timeout(Span(5, Seconds))) {
              case DynamicRequest(header, body) ⇒
                header shouldBe RequestHeader("/test", Method.GET, None, "messageId", Some("correlationId"))
                body shouldBe DynamicBody(Text("haha"))
            }
          }
        }
      } catch {
        case ex: Throwable ⇒
          fail(ex)
      } finally {
        system.shutdown()
        system.awaitTermination()
      }
    }

    "websocket: applyInputFilters non-empty filterChain, non-empty headers" in {
      implicit val system = ActorSystem()

      val host = "localhost"
      val port = 12345
      val url = "/testFilterChain"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))
      val onClientUpgradePromise = Promise[Boolean]()

      val client = system.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = ()

        override def onUpgrade: Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val filteredDynamicRequestPromise = Promise[DynamicRequest]()
      def exposeDynamicRequest: (DynamicRequest ⇒ Unit) = { filteredDynamicRequest ⇒
        filteredDynamicRequestPromise.complete(Success(filteredDynamicRequest))
      }
      val server = system.actorOf(Props(wsWorker(FilterChain(Seq(new TestInputFilter)), FilterChain(), exposeDynamicRequest)), "websocket-worker")

      val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
        case b: Http.Bound ⇒
          Future.successful(b)
      }
      whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
        client ! Connect()

        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
          client ! DynamicRequest(RequestHeader("/test", Method.GET, None, "messageId", Some("correlationId")), DynamicBody(Text("haha")))

          try {
            whenReady(filteredDynamicRequestPromise.future, Timeout(Span(5, Seconds))) {
              case DynamicRequest(header, body) ⇒
                header shouldBe RequestHeader("/test", Method.GET, None, "messageId", None)
                body shouldBe DynamicBody(Text("haha"))
            }
          } catch {
            case ex: Throwable ⇒ fail(ex)
          } finally {
            system.shutdown()
            system.awaitTermination()
          }
        }
      }
    }

    "websocket: applyOutputFilters empty filterChain, non-empty headers" in {
      implicit val system = ActorSystem()

      val host = "localhost"
      val port = 12345
      val url = "/testFilterChain"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))
      val onClientUpgradePromise = Promise[Boolean]()
      val onClientReceivedPromise = Promise[DynamicRequest]()

      val client = system.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
          onClientReceivedPromise.complete(Success(toDynamicRequest(frame)))
        }

        override def onUpgrade: Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val server = system.actorOf(Props(wsWorker(FilterChain(), FilterChain())), "websocket-worker")

      val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
        case b: Http.Bound ⇒
          Future.successful(b)
      }
      whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
        client ! Connect()

        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
          server ! DynamicRequest(RequestHeader("/test", Method.GET, None, "messageId", Some("correlationId")), DynamicBody(Text("haha")))

          try {
            whenReady(onClientReceivedPromise.future, Timeout(Span(5, Seconds))) {
              case DynamicRequest(header, body) ⇒
                header shouldBe RequestHeader("/test", Method.GET, None, "messageId", Some("correlationId"))
                body shouldBe DynamicBody(Text("haha"))
            }
          } catch {
            case ex: Throwable ⇒ fail(ex)
          } finally {
            system.shutdown()
            system.awaitTermination()
          }
        }
      }
    }

    "websocket: applyOutputFilters non-empty filterChain, non-empty headers" in {
      implicit val system = ActorSystem()

      val host = "localhost"
      val port = 12345
      val url = "/testFilterChain"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, upgradeHeaders(host, port))
      val onClientUpgradePromise = Promise[Boolean]()
      val onClientReceivedPromise = Promise[DynamicRequest]()

      val client = system.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = {
            onClientReceivedPromise.complete(Success(toDynamicRequest(frame)))
        }

        override def onUpgrade: Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val server = system.actorOf(Props(wsWorker(FilterChain(), FilterChain(Seq(new TestOutputFilter)))), "websocket-worker")

      val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
        case b: Http.Bound ⇒
          Future.successful(b)
      }
      whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
        client ! Connect()

        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
          server ! DynamicRequest(RequestHeader("/test", Method.GET, None, "messageId", Some("correlationId")), DynamicBody(Text("haha")))

          try {
            whenReady(onClientReceivedPromise.future, Timeout(Span(5, Seconds))) {
              case DynamicRequest(header, body) ⇒
                header shouldBe RequestHeader("/test", Method.GET, None, "messageId", None)
                body shouldBe DynamicBody(Text("haha"))
            }
          } catch {
            case ex: Throwable ⇒ fail(ex)
          } finally {
            system.shutdown()
            system.awaitTermination()
          }
        }
      }
    }
  }

  def wsWorker(inputFilterChain: FilterChain,
               outputFilterChain: FilterChain,
               exposeDynamicRequestFunction: (DynamicRequest ⇒ Unit) = _ => (),
               exposeHttpRequestFunction: (HttpRequest ⇒ Unit) = _ => ()): Actor = {
    new WsTestWorker(inputFilterChain, outputFilterChain) {
      override def exposeDynamicRequest(dynamicRequest: DynamicRequest) = exposeDynamicRequestFunction(dynamicRequest)

      override def exposeHttpRequest(request: HttpRequest) = exposeHttpRequest(request)
    }
  }

  def upgradeHeaders(host: String, port: Int) = List(
    HttpHeaders.Host(host, port),
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate"))
}
