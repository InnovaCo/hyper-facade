package eu.inn.facade.filter.chain

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import eu.inn.binders.value.{Null, Text}
import eu.inn.facade.filter.model.{RequestFilter, ResponseFilter}
import eu.inn.facade.http.{Connect, WsTestClient, WsTestWorker}
import eu.inn.facade.model._
import eu.inn.hyperbus.model.Method
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Success

class WsFilterChainTest extends FreeSpec with Matchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  class TestRequestFilter extends RequestFilter {
    override def  apply(context: FacadeRequestContext, input: FacadeRequest)
                       (implicit ec: ExecutionContext): Future[FacadeRequest] = {
      if (input.headers.nonEmpty) {
        Future(input.copy(
          headers = input.headers.filterNot{ _._1 == "toBeFiltered" }
        ))
      }
      else {
        Future.failed(new FilterInterruptException(
          response = FacadeResponse(403, Map.empty, Text("Forbidden")),
          message = "Forbidden by filter"
        ))
      }
    }
  }

  class TestResponseFilter extends ResponseFilter {
    override def apply(context: FacadeRequestContext, output: FacadeResponse)
                      (implicit ec: ExecutionContext): Future[FacadeResponse] = {
      if (output.headers.nonEmpty) {
        Future(output.copy(
          headers = output.headers.filterNot { _._1 == "toBeFiltered" }
        ))
      }
      else {
        Future.failed(new FilterInterruptException(
          response = FacadeResponse(200, Map("x-http-header" → Seq("Accept-Language")), Null),
          message = "Interrupted by filter"
        ))
      }
    }
  }

  val filterChain = new SimpleFilterChain(
    requestFilters = Seq(new TestRequestFilter),
    responseFilters = Seq(new TestResponseFilter)
  ) // todo: + test eventFilters

  val emptyFilterChain = new SimpleFilterChain()

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

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val filteredFacadeRequestPromise = Promise[FacadeRequest]()
      def exposeFacadeRequest: (FacadeRequest ⇒ Unit) = { filteredDynamicRequest ⇒
        filteredFacadeRequestPromise.complete(Success(filteredDynamicRequest))
      }
      val server = system.actorOf(Props(wsWorker(emptyFilterChain, exposeFacadeRequest)), "websocket-worker")
      try {
        val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
          case b: Http.Bound ⇒
            Future.successful(b)
        }
        whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
          client ! Connect()

          whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
            client ! FacadeRequest(
                Uri("/test"), Method.GET,
                Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
                  FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId"),
                  "toBeFiltered" → Seq("This header should be dropped by filter")
                ),Text("haha"))

            whenReady(filteredFacadeRequestPromise.future, Timeout(Span(5, Seconds))) {
              case FacadeRequest(uri, method, headers, body) ⇒
                uri shouldBe Uri("/test")
                headers(FacadeHeaders.CLIENT_MESSAGE_ID) shouldBe Seq("messageId")
                headers(FacadeHeaders.CLIENT_CORRELATION_ID) shouldBe Seq("correlationId")
                body shouldBe Text("haha")
            }
          }
        }
      } catch {
        case ex: Throwable ⇒
          fail(ex)
      } finally {
        Await.result(system.terminate(), Duration.Inf)
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

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val filteredFacadeRequestPromise = Promise[FacadeRequest]()
      def exposeFacadeRequest: (FacadeRequest ⇒ Unit) = { filteredFacadeRequest ⇒
        filteredFacadeRequestPromise.complete(Success(filteredFacadeRequest))
      }
      val server = system.actorOf(Props(wsWorker(filterChain, exposeFacadeRequest)), "websocket-worker")

      val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
        case b: Http.Bound ⇒
          Future.successful(b)
      }
      whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
        client ! Connect()

        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
          client ! FacadeRequest(Uri("/test"), Method.GET,
                Map(FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
                FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")), Text("haha"))

          try {
            whenReady(filteredFacadeRequestPromise.future, Timeout(Span(5, Seconds))) {
              case FacadeRequest(uri, method, headers, body) ⇒
                uri shouldBe Uri("/test")
                method shouldBe Method.GET
                headers(FacadeHeaders.CLIENT_MESSAGE_ID) shouldBe Seq("messageId")
                headers(FacadeHeaders.CLIENT_CORRELATION_ID) shouldBe Seq("correlationId")
                body shouldBe Text("haha")
            }
          } catch {
            case ex: Throwable ⇒ fail(ex)
          } finally {
            Await.result(system.terminate(), Duration.Inf)
          }
        }
      }
    }

      /* todo: what we're testing here?
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

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val server = system.actorOf(Props(wsWorker(RequestFilterChain(), ResponseFilterChain())), "websocket-worker")

      val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
        case b: Http.Bound ⇒
          Future.successful(b)
      }
      whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
        client ! Connect()

        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
          server ! DynamicRequest(
            RequestHeader(
              Uri("/test"),
              Map(Header.METHOD → Seq(Method.GET),
                Header.MESSAGE_ID → Seq("messageId"),
                Header.CORRELATION_ID → Seq("correlationId"),
                "toBeFiltered" → Seq("This header should be dropped by filter"))
            ),
            DynamicBody(Text("haha")))

          try {
            whenReady(onClientReceivedPromise.future, Timeout(Span(5, Seconds))) {
              case DynamicRequest(uri, body, headers) ⇒
                uri shouldBe Uri("/test")
                headers(Header.METHOD) shouldBe Seq(Method.GET)
                headers(Header.MESSAGE_ID) shouldBe Seq("messageId")
                headers(Header.CORRELATION_ID) shouldBe Seq("correlationId")
                body shouldBe DynamicBody(Text("haha"))
            }
          } catch {
            case ex: Throwable ⇒ fail(ex)
          } finally {
            Await.result(system.terminate(), Duration.Inf)
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

        override def onUpgrade(): Unit = {
          onClientUpgradePromise.complete(Success(true))
        }
      }), "websocket-client")

      val server = system.actorOf(Props(wsWorker(RequestFilterChain(), ResponseFilterChain(Seq(new TestOutputFilter)))), "websocket-worker")

      val binding = IO(UHttp).ask(Http.Bind(server, host, port))(akka.util.Timeout(10, TimeUnit.SECONDS)) flatMap {
        case b: Http.Bound ⇒
          Future.successful(b)
      }
      whenReady(binding, Timeout(Span(10, Seconds))) { b ⇒
        client ! Connect()

        whenReady(onClientUpgradePromise.future, Timeout(Span(5, Seconds))) { result ⇒
          server ! DynamicRequest(
            RequestHeader(
              Uri("/test"),
              Map(Header.METHOD → Seq(Method.GET),
                Header.MESSAGE_ID → Seq("messageId"),
                Header.CORRELATION_ID → Seq("correlationId"))
            ),
            DynamicBody(Text("haha")))

          try {
            whenReady(onClientReceivedPromise.future, Timeout(Span(5, Seconds))) {
              case DynamicRequest(uri, body, headers) ⇒
                uri shouldBe Uri("/test")
                headers(Header.METHOD) shouldBe Seq(Method.GET)
                headers(Header.MESSAGE_ID) shouldBe Seq("messageId")
                headers(Header.CORRELATION_ID) shouldBe Seq("correlationId")
                body shouldBe DynamicBody(Text("haha"))
            }
          } catch {
            case ex: Throwable ⇒ fail(ex)
          } finally {
            Await.result(system.terminate(), Duration.Inf)
          }
        }
      }
    }*/
  }

  def wsWorker(filterChain: FilterChain,
               exposeFacadeRequestFunction: (FacadeRequest ⇒ Unit) = _ => ()): Actor = {
    new WsTestWorker(filterChain) {
      override def exposeFacadeRequest(facadeRequest: FacadeRequest) = exposeFacadeRequestFunction(facadeRequest)
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
