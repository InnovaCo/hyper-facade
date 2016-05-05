package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.config.ConfigLoader
import eu.inn.facade.ConfigsFactory
import eu.inn.facade.raml.{RamlConfig, RewriteIndexHolder}
import scaldi.{Module, ShutdownHookLifecycleManager}

class ConfigModule extends Module {
  bind [Config]     identifiedBy 'config toNonLazy ConfigLoader()
  bind [RamlConfig] identifiedBy 'raml   to ConfigsFactory.ramlConfig(inject [Config] )
}
