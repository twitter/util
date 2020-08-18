package com.twitter.inject

import com.google.inject.Module

private[inject] trait TwitterBaseModule extends TwitterModuleFlags with TwitterModuleLifecycle {

  /**
   * Additional modules to be composed into this module. This list of modules is generally used
   * ''instead'' of the [[com.twitter.inject.TwitterModule.install]] method to properly support the
   * [[TwitterModuleLifecycle]] for [[com.twitter.inject.TwitterModule]] instances.
   *
   * @note [[com.twitter.inject.TwitterModule.install(module: Module)]] can still be used for non-[[TwitterModule]] 1
   *       instances, and is sometimes preferred due to `install` being deferred until after flag parsing occurs.
   * @note Java users should prefer [[javaModules]].
   */
  protected[inject] def modules: Seq[Module] = Seq.empty

  /** Additional modules to be composed into this module from Java */
  protected[inject] def javaModules: java.util.Collection[Module] =
    java.util.Collections.emptyList[Module]()

  /** Additional framework modules to be composed into this module. */
  protected[inject] def frameworkModules: Seq[Module] = Seq.empty
}
