package eu.inn.facade.raml

import com.typesafe.config.Config
import eu.inn.facade.FacadeConfigPaths
import eu.inn.hyperbus.transport.api.uri.Uri

class RamlConfigurationReader(ramlConfiguration: RamlConfiguration, config: Config) {

  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method).map(foundTrait ⇒ foundTrait.name).distinct
  }

  def resourceUri(requestUri: Uri, method: String): Uri = {
    val uri = resourceUri(requestUri)
    val isStrictRaml = System.getProperty(FacadeConfigPaths.RAML_STRICT_CONFIG, config.getString(FacadeConfigPaths.RAML_STRICT_CONFIG)).toBoolean
    if (isStrictRaml) {
      val foundUri = ramlConfiguration.resourcesByUri.get(uri.pattern.specific) match {
        case Some(resourceConfig) ⇒
          if(resourceConfig.methods.isEmpty || resourceConfig.methods.contains(Method(method)))
            Some(uri)
          else
            None
        case None ⇒
          None
      }
      if (foundUri.isEmpty)
        throw RamlStrictConfigException(s"resource '$requestUri' with method '$method' is not configured in RAML configuration")
    }
    uri
  }

  private def resourceUri(requestUri: Uri): Uri = {
    //todo: lookup in map instead of sequence!
    val formattedUri = Uri(requestUri.formatted)
    UriMatcher.matchUri(formattedUri, ramlConfiguration.uris).getOrElse(requestUri)
  }

  private def traits(uriPattern: String, method: String): Seq[Trait] = {
    ramlConfiguration.resourcesByUri.get(uriPattern) match {
      case Some(configuration) ⇒
        val traits = configuration.traits
        traits.methodSpecificTraits.getOrElse(Method(method), Seq.empty) ++ traits.commonTraits
      case None ⇒ Seq()
    }
  }
}
