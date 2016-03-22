package eu.inn.facade.filter.http

import eu.inn.binders.dynamic.{Lst, Obj, ObjV, Value}
import eu.inn.facade.model._
import eu.inn.facade.utils.NamingUtils
import eu.inn.hyperbus.model.Header
import eu.inn.hyperbus.transport.api.matchers.{Specific, TextMatcher}
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter extends ResponseFilter {

  override def apply(context: ResponseFilterContext, response: FacadeResponse)
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

      val newBody = if (response.body.isInstanceOf[Obj]) {
        Obj(response.body.asMap.map {
          case ("_links", value) ⇒
            "_links" → {
              value match {
                case Obj(links) ⇒ Obj(transformLinks(links, response.body))
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
        response.body
      }

      response.copy(
        headers = headersBuilder.result(),
        body = newBody
      )
    }
  }

  def transformLink(linkValue: Value, body: Value): Value = {
    if (linkValue.templated[Option[Boolean]].contains(true)) { // templated link, have to format
    val href = linkValue.href[String]
      val tokens = UriParser.extractParameters(href)
      val args = tokens.map { arg ⇒
        arg → linkValue.selectDynamic[String](arg)             // todo: support inner fields
      } toMap
      val uri = Uri(href, args)
      ObjV("href" → uri.formatted)
    } else {
      linkValue
    }
  }

  def transformLinks(links: scala.collection.Map[String, Value], body: Value) : scala.collection.Map[String, Value] = {
    links map {
      case (name, links : Lst) ⇒ // json+hal when link is array
        name → Lst(links.v.map(transformLink(_, body)))
      case (name, link) ⇒ // json+hal - single link
        name → transformLink(link, body)
    }
  }

  def transformEmbedded(embedded: scala.collection.Map[String, Value]) : scala.collection.Map[String, Value] = {
    embedded map { case (name, value) ⇒

    }
  }
}


object HttpWsResponseFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}
