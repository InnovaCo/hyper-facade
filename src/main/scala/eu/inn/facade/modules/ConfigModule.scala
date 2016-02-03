package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.raml.RamlConfig
import scaldi.{OpenInjectable, Module}

class ConfigModule extends Module {

  bind [Config]     identifiedBy 'config toNonLazy new ConfigsFactory().config
  bind [RamlConfig] identifiedBy 'raml   toNonLazy new ConfigsFactory().ramlConfig(inject [Config] )
}
