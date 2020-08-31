package com.twitter.inject

import com.twitter.util.logging.Logging
import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * [[com.twitter.inject.TwitterModule]] to com.twitter.inject.app.App lifecycle integration.
 */
private[inject] trait TwitterModuleLifecycle extends Logging {

  /* Mutable State */
  private lazy val closableFunctions = mutable.Buffer[() => Unit]()

  /* Protected */

  /**
   * Invoked after the injector is started.
   *
   * This method should only get singleton instances from the injector.
   */
  protected[inject] def singletonStartup(injector: Injector): Unit = {}

  /**
   * Invoke after external ports are bound and any clients are resolved
   *
   * This method should only get singleton instances from the injector.
   */
  protected[inject] def singletonPostWarmupComplete(injector: Injector): Unit = {}

  /**
   * Invoked on graceful shutdown of the application.
   *
   * This method should only get singleton instances from the injector.
   */
  protected[inject] def singletonShutdown(injector: Injector): Unit = {}

  /**
   * Collects functions over a [[com.twitter.util.Closable]]s. These functions will be passed to
   * the application `onExit` function to be executed on graceful shutdown of the application.
   *
   * @note It is expected that the passed function is a function over a [[com.twitter.util.Closable]].
   *
   * @param f A Function0 which returns Unit. It is expected that this function encapsulates awaiting
   *          on a [[com.twitter.util.Closable]] that the application would like to ensure is closed
   *          upon graceful shutdown.
   *
   * {{{
   *   closeOnExit {
   *     val closable = ...
   *     Await.result(
   *       closable.close(after: Duration), timeout: Duration)
   *     }
   * }}}
   *
   * @see [[com.twitter.app.App.onExit(f: => Unit)]]
   * @see [[com.twitter.util.Awaitable]]
   * @see [[com.twitter.util.Closable]]
   */
  protected def closeOnExit(f: => Unit): Unit = {
    // `c.t.app.App#onExit` will swallow any exception from a given function, thus we try to ensure
    // some visibility into any potential exception resulting from executing the passed function.
    def tryCatchFn(fn: => Unit): () => Unit = { () =>
      {
        try {
          fn
        } catch {
          case NonFatal(e) =>
            warn(e.getMessage)
            debug(s"An error occurred in ${getClass.getSimpleName}#closeOnExit", e)
        }
      }
    }

    closableFunctions += tryCatchFn(f)
  }

  /* Private */

  private[inject] def close(): Unit = {
    closableFunctions.foreach(_())
  }
}
