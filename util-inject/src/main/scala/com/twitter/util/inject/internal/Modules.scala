package com.twitter.inject.internal

import com.google.inject.{Guice, Module, Stage}
import com.twitter.app.{Flag, Flags}
import com.twitter.inject.{Injector, TwitterBaseModule}
import com.twitter.inject.internal.iterable._
import scala.collection.JavaConverters._

private[inject] object Modules {

  /**
   * De-duplicate a given sequence of  [[com.google.inject.Module]].
   * Exposed for testing.
   */
  def distinctModules(
    modules: Seq[com.google.inject.Module]
  ): Seq[com.google.inject.Module] = {
    // De-dupe using a `java.util.IdentityHashMap` which uses reference equality instead
    // of object equality  to check if both objects point to the same memory location
    // and filter out modules already seen. We use `filter` because it's stable, and
    // the order of module initialization may be important.
    val identityHashMap = new java.util.IdentityHashMap[com.google.inject.Module, Boolean]()
    modules.filter { module =>
      if (identityHashMap.containsKey(module)) false
      else {
        identityHashMap.put(module, true)
        true
      }
    }
  }

  /** Capture all flags in the [[com.google.inject.Module]] object hierarchy. */
  private def findModuleFlags(modules: Seq[com.google.inject.Module]): Seq[Flag[_]] = {
    // Flags are stored in the App `com.twitter.app.Flags` member variable which is a
    // Map[String, Flag[_]], where the key is the Flag#name. Thus, to ensure we correctly account
    // for the "override" behavior of Flag#add (the last Flag with the same name added, wins),
    // we need to ensure that we "reverse" our Sequence before performing a distinct operation
    // discriminated by Flag#name.
    modules
      .collect {
        case injectModule: TwitterBaseModule => injectModule.flags
      }.flatten.reverse.distinctBy(_.name)
  }

  /** Recursively find all 'composed' modules */
  private def findInstalledModules(
    module: com.google.inject.Module
  ): Seq[com.google.inject.Module] = module match {
    case injectModule: TwitterBaseModule =>
      injectModule.modules ++
        injectModule.modules.flatMap(findInstalledModules) ++
        injectModule.javaModules.asScala.toSeq ++
        injectModule.javaModules.asScala.toSeq.flatMap(findInstalledModules) ++
        injectModule.frameworkModules ++
        injectModule.frameworkModules.flatMap(findInstalledModules)
    case _ =>
      Seq.empty
  }

}

/**
 * Provides a flattened view of the given lists of [[Module]]s. That is if the [[Module]] is
 * a [[[com.twitter.inject.TwitterBaseModule]] type it may reference other [[Module]]s to
 * be included when creating the injector. This class will perform the recursive lookup of
 * all the referenced [[Module]]s from the give input lists and store each as a flattened
 * sequence to be used for injector creation.
 *
 * We only want to compute all of the composed modules and the flags contained therein
 * once as calling the [[com.twitter.inject.TwitterBaseModule#modules]] or
 * [[com.twitter.inject.TwitterBaseModule#javaModules]] or
 * [[com.twitter.inject.TwitterBaseModule#frameworkModules]] methods is not guaranteed
 * to be idempotent. This class is a holder of the computed modules and flags for
 * the containing [[com.twitter.inject.app.App]].
 *
 * @see [[com.twitter.inject.TwitterBaseModule]]
 * @see [[com.twitter.inject.TwitterBaseModule#modules]]
 * @see [[com.twitter.inject.TwitterBaseModule#javaModules]]
 * @see [[com.twitter.inject.TwitterBaseModule#frameworkModules]]
 */
private[inject] class Modules(required: Seq[Module], overrides: Seq[Module]) {
  import Modules._

  val modules: Seq[Module] = {
    val composedModules = required.flatMap(findInstalledModules)
    required ++ composedModules
  }

  val overrideModules: Seq[Module] = {
    val composedOverrideModules = overrides.flatMap(findInstalledModules)
    overrides ++ composedOverrideModules
  }

  val moduleFlags: Seq[Flag[_]] =
    findModuleFlags(distinctModules(modules ++ overrideModules))

  def addFlags(flag: Flags): Unit = moduleFlags.foreach(flag.add)

  def install(
    flags: Flags,
    stage: Stage
  ): InstalledModules = {
    // ensure we add the FlagsModule and the TwitterTypeConvertersModule to the list to build the injector.
    val requiredModules = modules ++ Seq(new FlagsModule(flags), TwitterTypeConvertersModule)
    val combinedModule = com.google.inject.util.Modules
      .`override`(requiredModules.asJava).`with`(overrideModules.asJava)

    InstalledModules(
      injector = Injector(Guice.createInjector(stage, combinedModule)),
      modules = distinctModules(requiredModules ++ overrideModules)
    )
  }
}
