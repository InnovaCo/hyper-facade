package eu.inn.facade.modules

import scaldi.Injector

object Injectors {

  def apply(): Injector = {
    val injector = new ConfigModule :: new FiltersModule :: new ServiceModule
    injector.initNonLazy()
  }
}
