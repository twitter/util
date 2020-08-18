package com.twitter.inject

import com.google.inject.{Provides, Injector => GuiceInjector}
import javax.inject.Singleton

object InjectorModule extends TwitterModule {

  @Provides
  @Singleton
  private def providesInjector(injector: GuiceInjector): Injector = {
    Injector(injector)
  }

  /**  Java-friendly way to access this module as a singleton instance */
  def get(): this.type = this
}
