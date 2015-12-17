package eu.inn.facade.injectors

import com.typesafe.config.Config
import eu.inn.facade.ConfigComponent
import eu.inn.facade.raml.RamlConfig
import scaldi.Module

class ConfigModule extends Module {

  bind [Config]     identifiedBy "config" to ConfigComponent.config
  bind [RamlConfig] identifiedBy "raml"   to ConfigComponent.ramlConfig(inject [Config] )
}
