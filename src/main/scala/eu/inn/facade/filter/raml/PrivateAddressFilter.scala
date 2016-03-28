package eu.inn.facade.filter.raml

import com.typesafe.config.Config
import eu.inn.facade.model.{FacadeRequest, RequestFilter, RequestFilterContext}

import scala.concurrent.{ExecutionContext, Future}

class PrivateAddressFilter(config: Config) extends RequestFilter {
  override def apply(context: RequestFilterContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future{request}
  }
}

case class Address(ip: String)

case class NetworkRange(from: String, to: String) {
  def contains(ip: String): Boolean = {
    true
  }
}
