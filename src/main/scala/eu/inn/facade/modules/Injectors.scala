package eu.inn.facade.modules

import eu.inn.config.ConfigLoader
import eu.inn.facade.FacadeConfig
import scaldi.Injector

import scala.collection.JavaConversions._

object Injectors {
  def apply(): Injector = {
    val injector = new ConfigModule :: new FiltersModule :: loadConfigInjectedModules(new ServiceModule)
    injector.initNonLazy()
  }

  def loadConfigInjectedModules(previous: Injector): Injector = {
    val config = ConfigLoader() // todo: replace with inject  if possible

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
