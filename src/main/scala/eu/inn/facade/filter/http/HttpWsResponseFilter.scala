package eu.inn.facade.filter.http

import eu.inn.binders.value._
import eu.inn.facade.filter.FilterContext
import eu.inn.facade.model._
import eu.inn.hyperbus.model.Links.LinksMap
import eu.inn.hyperbus.model.{DefLink, Header}
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}
import spray.http.HttpHeaders

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter extends ResponseFilter {

  override def apply(context: FilterContext, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val headersBuilder = Map.newBuilder[String, Seq[String]]
      response.headers.foreach {
        case (Header.CONTENT_TYPE, value :: tail) ⇒
          headersBuilder += FacadeHeaders.CONTENT_TYPE → Seq(
            FacadeHeaders.CERTAIN_CONTENT_TYPE_START + value + FacadeHeaders.CERTAIN_CONTENT_TYPE_END
          )

        case (k, v) ⇒
          if (HttpWsResponseFilter.directHyperbusToFacade.contains(k)) {
            headersBuilder += HttpWsResponseFilter.directHyperbusToFacade(k) → v
          }
      }

      val newBody = transformEmbeddedObject(response.body)
      if (newBody.isInstanceOf[Obj]/* && response.status == 201*/) { // Created, set header value
        val link = newBody.__links.fromValue[Option[LinksMap]].flatMap(_.get(DefLink.LOCATION)) match {
          case Some(Left(l)) ⇒
            headersBuilder += (HttpHeaders.Location.name → Seq(l.href))
          case Some(Right(la)) ⇒
            headersBuilder += (HttpHeaders.Location.name → Seq(la.head.href))
          case _ ⇒
        }
      }

      response.copy(
        headers = headersBuilder.result(),
        body = newBody
      )
    }
  }

  def transformLinks(links: scala.collection.Map[String, Value], body: Value) : scala.collection.Map[String, Value] = {
    links map {
      case (name, links : Lst) ⇒ // json+hal when link is array
        name → Lst(links.v.map(transformLink(_, body)))
      case (name, link : Obj) ⇒ // json+hal - single link
        name → transformLink(link, body)
      case (name, something) ⇒
        name → something
    }
  }

  def transformEmbedded(embedded: scala.collection.Map[String, Value]) : scala.collection.Map[String, Value] = {
    embedded map {
      case (name, array : Lst) ⇒
        name → Lst(array.v.map(transformEmbeddedObject(_)))
      case (name, obj : Obj) ⇒
        name → transformEmbeddedObject(obj)
      case (name, something : Value) ⇒
        name → something
    }
  }

  def transformLink(linkValue: Value, body: Value): Value = {
    if (linkValue.templated.fromValue[Option[Boolean]].contains(true)) { // templated link, have to format
    val href = linkValue.href.asString
      val tokens = UriParser.extractParameters(href)
      val args = tokens.map { arg ⇒
        arg → body.asMap(arg).asString             // todo: support inner fields + handle exception if not exists?
      } toMap
      val uri = Uri(href, args)
      ObjV("href" → uri.formatted)
    } else {
      linkValue
    }
  }

  def transformEmbeddedObject(obj: Value): Value = {
    if (obj.isInstanceOf[Obj]) {
      Obj(obj.asMap.map {
        case ("_links", value) ⇒
          "_links" → {
            value match {
              case Obj(links) ⇒ Obj(transformLinks(links, obj))
              case other ⇒ other
            }
          }

        case ("_embedded", value) ⇒
          "_embedded" → {
            value match {
              case Obj(embedded) ⇒ Obj(transformEmbedded(embedded))
              case other ⇒ other
            }
          }
        case (key, value) ⇒
          key → value
      })
    } else {
      obj
    }
  }

}

object HttpWsResponseFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}
