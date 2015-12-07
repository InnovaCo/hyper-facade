package eu.inn.facade.filter.http

import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import akka.io.IO
import eu.inn.binders.dynamic.Text
import eu.inn.facade.filter.FilterNotPassedException
import eu.inn.facade.filter.RequestMapper._
import eu.inn.facade.filter.chain.FilterChain
import eu.inn.facade.filter.model.{Headers, Filter}
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.DynamicRequest
import eu.inn.hyperbus.serialization.RequestHeader
import org.scalatest.{FreeSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.{TextFrame, Frame}
import spray.http.{HttpMethods, HttpRequest, HttpHeaders}
import scala.concurrent.{Promise, Future}
import scala.util.Success

class WsFilterChainTest extends FreeSpec with Matchers with ScalaFutures {
  import scala.concurrent.ExecutionContext.Implicits.global

  class TestInputFilter extends Filter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (requestHeaders.headers.nonEmpty) Future(requestHeaders, body)
      else Future(requestHeaders withResponseCode Some(403), DynamicBody(Text("Forbidden")))
    }
  }

  class TestFailedFilter extends Filter {
    override def apply(requestHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      throw new FilterNotPassedException(403, "Forbidden")
    }
  }

  class TestOutputFilter extends Filter {
    override def apply(responseHeaders: Headers, body: DynamicBody): Future[(Headers, DynamicBody)] = {
      if (responseHeaders.headers.nonEmpty) Future(responseHeaders, body)
      else Future(Headers(Map("x-http-header" → "Accept-Language"), Some(200)), null)
    }
  }

  "WsFilterChain " - {
    "applyInputFilters empty headers" in {
      implicit val system = ActorSystem()
      import system.dispatcher

      val host = "localhost"
      val port = 54321
      val url = "/testFilterChain"

      val connect = Http.Connect(host, port)
      val onUpgradeGetReq = HttpRequest(HttpMethods.GET, url, headers(host, port))

      val clientPromise = Promise[DynamicRequest]()
      
      val clientActor = new WsTestClient(connect, onUpgradeGetReq) {
        override def onMessage(frame: TextFrame): Unit = clientPromise.complete(Success(toDynamicRequest(frame)))
      }
      val client = system.actorOf(Props(clientActor), "websocket-client")
      val wsWorkerActor = wsWorker(client, FilterChain(), {() ⇒ clientActor.initConnection})
//      { (requestHeader, dynamicBody) ⇒
//        wsWorkerPromise.complete(Success((requestHeader, dynamicBody)))
//      }
      val listener = system.actorOf(Props(wsWorkerActor), "websocket-worker")
      val serverActor = new WsTestServer(listener)
      val server = system.actorOf(Props(serverActor), "websocket-server")

      IO(UHttp) ! Http.Bind(server, host, port)

      

      system.shutdown()
      system.awaitTermination()
    }
  }

  def wsWorker(clientActor: ActorRef, filterChain: FilterChain,
      onConnected: () ⇒ _,
      exposeHeadersFunction: ((RequestHeader, DynamicBody) ⇒ Unit) = ((_,_) => ()),
      exposeRequestFunction: (HttpRequest ⇒ Unit) = (_ => ())): Actor = {
    new WsTestWorker(clientActor, filterChain, onConnected) {
      override def filterChain(url: String): FilterChain = filterChain
      override def exposeDynamicRequest(requestHeader: RequestHeader, dynamicBody: DynamicBody) = exposeHeadersFunction(requestHeader, dynamicBody)
      override def exposeHttpRequest(request: HttpRequest) = exposeHttpRequest(request)
    }
  }

  def headers(host: String, port: Int) = List(
    HttpHeaders.Host(host, port),
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate"))
}
