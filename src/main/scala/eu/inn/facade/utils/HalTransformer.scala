package eu.inn.facade.utils

import eu.inn.binders.value._
import eu.inn.hyperbus.transport.api.uri.{Uri, UriParser}

object HalTransformer {

  def transformAndFormatEmbeddedObject(obj: Value, transformUri: (Uri ⇒ Uri) = uri ⇒ uri): Value = {
    transformEmbeddedObj(obj, formatLinks = true, transformUri)
  }

  def transformEmbeddedObject(obj: Value, transformUri: (Uri ⇒ Uri) = uri ⇒ uri): Value = {
    transformEmbeddedObj(obj, formatLinks = false, transformUri)
  }

  private def transformEmbeddedObj(obj: Value, formatLinks: Boolean, transformUri: (Uri ⇒ Uri) = uri ⇒ uri): Value = {
    if (obj.isInstanceOf[Obj]) {
      Obj(obj.asMap.map {
        case ("_links", value) ⇒
          "_links" → {
            value match {
              case Obj(links) ⇒ Obj(transformLinks(links, obj, formatLinks, transformUri))
              case other ⇒ other
            }
          }

        case ("_embedded", value) ⇒
          "_embedded" → {
            value match {
              case Obj(embedded) ⇒ Obj(transformEmbedded(embedded, formatLinks))
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

  def transformLinks(links: scala.collection.Map[String, Value], body: Value, formatLinks: Boolean, transformUri: Uri ⇒ Uri) : scala.collection.Map[String, Value] = {
    links map {
      case (name, links : Lst) ⇒ // json+hal when link is array
        name → Lst(links.v.map(transformLink(_, body, formatLinks, transformUri)))
      case (name, link : Obj) ⇒ // json+hal - single link
        name → transformLink(link, body, formatLinks, transformUri)
      case (name, linkValue) ⇒
        val href = linkValue.href.asString
        name → ObjV("href" → transformUri(Uri(href)).pattern.specific)
    }
  }

  def transformEmbedded(embedded: scala.collection.Map[String, Value], formatLinks: Boolean) : scala.collection.Map[String, Value] = {
    embedded map {
      case (name, array : Lst) ⇒
        name → Lst(array.v.map(transformEmbeddedObj(_, formatLinks)))
      case (name, obj : Obj) ⇒
        name → transformEmbeddedObj(obj, formatLinks)
      case (name, something : Value) ⇒
        name → something
    }
  }

  def transformLink(linkValue: Value, body: Value, formatLinks: Boolean, transformUri: Uri ⇒ Uri): Value = {
    val href = linkValue.href.asString
    if (formatLinks && linkValue.templated.fromValue[Option[Boolean]].contains(true)) { // templated link, have to format
      val tokens = UriParser.extractParameters(href)
      val args = tokens.map { arg ⇒
        arg → body.asMap(arg).asString             // todo: support inner fields + handle exception if not exists?
      } toMap
      val uri = transformUri(Uri(href, args))
      ObjV("href" → uri.formatted)
    } else {
      val uri = transformUri(Uri(href)).pattern.specific
      ObjV("href" → uri)
    }
  }
}
