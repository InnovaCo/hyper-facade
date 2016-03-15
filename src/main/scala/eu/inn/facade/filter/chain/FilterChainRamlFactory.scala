package eu.inn.facade.filter.chain

import eu.inn.facade.model.{Filter, InputFilter, OutputFilter}
import eu.inn.facade.raml.{DataStructure, RamlConfig}
import eu.inn.hyperbus.transport.api.uri.Uri
import scaldi.{Injectable, Injector}
import scala.util.{Failure, Success, Try}

class FilterChainRamlFactory(implicit inj: Injector) extends FilterChainFactory with Injectable {

  val ramlConfig = inject[RamlConfig]

  override def inputFilterChain(uri: Uri, method: String, contentType: Option[String]): InputFilterChain = {
    val dataStructure = ramlConfig.requestDataStructure(uri.pattern.specific, method, contentType)
    val dataStructures: Seq[DataStructure] = dataStructure match {
      case Some(structure) ⇒ Seq(structure)
      case None ⇒ Seq()
    }
    val inputFilters = filters(uri, method, dataStructures).collect {
      case i : InputFilter ⇒ i
    }
    InputFilterChain(inputFilters)
  }

  override def outputFilterChain(uri: Uri, method: String): OutputFilterChain = {
    val dataStructures: Seq[DataStructure] = ramlConfig.responseDataStructures(uri, method)
    val outputFilters = filters(uri, method, dataStructures).collect {
      case o : OutputFilter ⇒ o
    } ++ defaultOutputFilters
    OutputFilterChain(outputFilters)
  }

  def defaultOutputFilters: Seq[OutputFilter] = {
    inject[Seq[OutputFilter]]("defaultOutputFilters")
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
  }
}
