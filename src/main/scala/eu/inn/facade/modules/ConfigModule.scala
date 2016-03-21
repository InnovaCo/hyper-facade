package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

class ConfigModule extends Module {
  bind [Config]     identifiedBy 'config to new ConfigsFactory().config
  bind [RamlConfig] identifiedBy 'raml   to new ConfigsFactory().ramlConfig(inject [Config] )
}
