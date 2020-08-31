package com.twitter.inject.internal

import com.google.inject.Module
import com.twitter.inject.{Injector, TwitterModuleLifecycle}
import com.twitter.util.logging.Logging
import scala.PartialFunction.condOpt

/**
 * A representation of the "installed" [[com.google.inject.Module]]s, that is
 * the created [[com.twitter.inject.Injector]] and the [[com.google.inject.Module]] of which it
 * is comprised. Creating the [[com.twitter.inject.Injector]] loses the [[com.google.inject.Module]]s
 * over which it was created, but we need to hold onto the list in order to apply the application lifecycle
 * to the [[com.twitter.inject.TwitterModuleLifecycle]] versions of the contained [[com.google.inject.Module]]s.
 *
 * @param injector the [[com.twitter.inject.Injector]] built from the [[com.google.inject.Module]]s
 * @param modules the list of [[com.google.inject.Module]]s from which the [[com.twitter.inject.Injector]] was created.
 */
private[inject] case class InstalledModules(injector: Injector, modules: Seq[Module])
    extends Logging {

  def postInjectorStartup(): Unit = {
    modules.foreach {
      case injectModule: TwitterModuleLifecycle =>
        try {
          injectModule.singletonStartup(injector)
        } catch {
          case e: Throwable =>
            error("Startup method error in " + injectModule, e)
            throw e
        }
      case _ =>
    }
  }

  def postWarmupComplete(): Unit = {
    modules.foreach {
      case injectModule: TwitterModuleLifecycle =>
        try {
          injectModule.singletonPostWarmupComplete(injector)
        } catch {
          case e: Throwable =>
            error("Post warmup complete method error in " + injectModule, e)
            throw e
        }
      case _ =>
    }
  }

  /**
   * Collect shutdown `ExitFunctions` for [[com.google.inject.Module]] instances
   * which implement the [[TwitterModuleLifecycle]]
   */
  def shutdown(): Seq[ExitFunction] = {
    condOptModules(modules)(_.singletonShutdown(injector))
  }

  /**
   * Collect close  `ExitFunctions` for [[com.google.inject.Module]] instances which
   * implement the [[TwitterModuleLifecycle]]
   */
  def close(): Seq[ExitFunction] = {
    condOptModules(modules)(_.close())
  }

  /* Private */

  /**
   * Iterates through the list of Modules to match only instances of TwitterModuleLifecycle
   * on which to create an `ExitFunction` over the passed in TwitterModuleLifecycle function.
   * @see [[scala.PartialFunction.condOpt]]
   */
  private[this] def condOptModules(
    modules: Seq[Module]
  )(
    fn: TwitterModuleLifecycle => Unit
  ): Seq[ExitFunction] = modules.flatMap { module =>
    condOpt(module) {
      case injectModule: TwitterModuleLifecycle =>
        () => fn(injectModule)
    }
  }
}
