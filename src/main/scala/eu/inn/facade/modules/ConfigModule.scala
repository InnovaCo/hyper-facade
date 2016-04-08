package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

class ConfigModule(config: Config) extends Module {
  bind [Config]     identifiedBy 'config toNonLazy config
  bind [RamlConfig] identifiedBy 'raml   to new ConfigsFactory().ramlConfig(inject [Config] )
}
