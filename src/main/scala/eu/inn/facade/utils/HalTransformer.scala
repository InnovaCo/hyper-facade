package eu.inn.facade.utils

import eu.inn.binders.value._
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}

object HalTransformer {

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
}
