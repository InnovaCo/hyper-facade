package eu.inn.facade.modules

import eu.inn.config.ConfigLoader
import eu.inn.facade.FacadeConfig
import eu.inn.metrics.modules.MetricsModule
import scaldi.Injector

import scala.collection.JavaConversions._

object Injectors {
  val config = ConfigLoader() // todo: replace with inject  if possible

  def apply(): Injector = {
    val injector = new ConfigModule(config) :: new FiltersModule :: loadConfigInjectedModules(new ServiceModule) :: new MetricsModule
    injector.initNonLazy()
  }

  def loadConfigInjectedModules(previous: Injector): Injector = {
    if (config.hasPath(FacadeConfig.INJECT_MODULES)) {
      var module = previous
      config.getStringList(FacadeConfig.INJECT_MODULES).foreach { injectModuleClassName ⇒
        module = module :: Class.forName(injectModuleClassName).newInstance().asInstanceOf[Injector]
      }
      module
    } else {
      previous
    }
  }
}
