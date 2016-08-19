package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.config.ConfigLoader
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.raml.{RamlConfiguration, RamlConfigurationReader}
import scaldi.Module

class ConfigModule extends Module {
  bind [Config]                  identifiedBy 'config     toNonLazy ConfigLoader()
  bind [RamlConfiguration]       identifiedBy 'raml       to ConfigsFactory.ramlConfig( inject [Config] )
  bind [RamlConfigurationReader] identifiedBy 'ramlReader to injected[RamlConfigurationReader]
}
