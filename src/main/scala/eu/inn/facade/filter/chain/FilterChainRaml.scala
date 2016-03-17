package eu.inn.facade.filter.chain

import eu.inn.facade.model._
import eu.inn.facade.raml.{DataStructure, RamlConfig}
import eu.inn.facade.utils.FutureUtils
import eu.inn.hyperbus.transport.api.uri.Uri
import scaldi.{Injectable, Injector}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class FilterChainRaml(ramlConfig: RamlConfig) extends FilterChain with Injectable {
  override def requestFilters(request: FacadeRequest): Seq[RequestFilter] = ???
  override def responseFilters(request: FacadeRequest, response: FacadeResponse): Seq[ResponseFilter] = ???
  override def eventFilters(request: FacadeRequest, event: FacadeRequest): Seq[EventFilter] = ???

/*
  override def requestFilterChain(input: FacadeRequest): RequestFilterChain = {
    val dataStructure = ramlConfig.requestDataStructure(input.uri.pattern.specific, input.method, input.contentType)
    val dataStructures: Seq[DataStructure] = dataStructure match {
      case Some(structure) ⇒ Seq(structure)
      case None ⇒ Seq()
    }
    val requestFilters = filters(input.uri, input.method, dataStructures).collect {
      case i : RequestFilter ⇒ i
    }
    RequestFilterChain(requestFilters)
  }

  // todo: + contentType ?
  def responseFilterChain(input: FacadeRequest, output: FacadeResponse): ResponseFilterChain = {
    val dataStructures: Seq[DataStructure] = ramlConfig.responseDataStructures(input.uri, input.method)
    val outputFilters = filters(input.uri, input.method, dataStructures).collect {
      case o : ResponseFilter ⇒ o
    } ++ defaultResponseFilters
    ResponseFilterChain(outputFilters)
  }

  def eventFilterChain(input: FacadeRequest, output: FacadeRequest): EventFilterChain = {
    EventFilterChain(Seq.empty)
  }

  def defaultResponseFilters: Seq[ResponseFilter] = {
    inject[Seq[ResponseFilter]]("defaultResponseFilters")
  }

  private def filters(uri: Uri, method: String, dataStructures: Seq[DataStructure]): Seq[Filter] = {
    val filterNames = ramlConfig.traitNames(uri.pattern.specific, method)
    val filters = filterNames.foldLeft(Seq[Filter]()) { (filters, filterName) ⇒
      Try(inject[Seq[Filter]](filterName)) match {
        case Success(traitBasedFilters) ⇒
          // we can map single filter on different traits, so the same filter should not be added twice
          val notAddedYetFilters = traitBasedFilters.filter(!filters.contains(_))
          filters ++ notAddedYetFilters
        case Failure(_) ⇒ filters
      }
    }
    dataStructures.foldLeft(filters) { (filters, dataStructure) ⇒
      dataStructure.body match {
        case Some(body) ⇒
          body.dataType.fields.foldLeft(filters) { (filters, field) ⇒
            field.dataType.annotations.foldLeft(filters) { (filters, annotation) ⇒
              Try(inject[Seq[Filter]](annotation.name)) match {
                case Success(annotationBasedFilters) ⇒
                  // we can map single filter on different annotations, so the same filter should not be added twice
                  val notAddedYetFilters = annotationBasedFilters.filter(!filters.contains(_))
                  filters ++ notAddedYetFilters
                case Failure(_) ⇒ filters
              }
            }
          }
        case None ⇒ filters
      }
    }
  }*/
}
