package eu.inn.facade.modules

import eu.inn.config.ConfigLoader
import eu.inn.facade.FacadeConfigPaths
import eu.inn.metrics.modules.MetricsModule
import scaldi.Injector

import scala.collection.JavaConversions._

object TestInjectors {
  def apply(): Injector = {
    val injector = new ConfigModule :: new FiltersModule :: loadConfigInjectedModules(new TestServiceModule) :: new MetricsModule
    injector.initNonLazy()
  }

  def loadConfigInjectedModules(previous: Injector): Injector = {
    val config = ConfigLoader() // todo: replace with inject  if possible

    if (config.hasPath(FacadeConfigPaths.INJECT_MODULES)) {
      var module = previous
      config.getStringList(FacadeConfigPaths.INJECT_MODULES).foreach { injectModuleClassName ⇒
        module = module :: Class.forName(injectModuleClassName).newInstance().asInstanceOf[Injector]
      }
      module
    } else {
      previous
    }
  }
}
