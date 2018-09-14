package com.twitter.util

/**
 * A Java friendly API for handling side-effects for a `Future`.
 *
 * If you want a Java API to
 * [[https://twitter.github.io/finagle/guide/Futures.html#sequential-composition sequence]]
 * the work you can use a [[FutureTransformer]].
 *
 * @see [[Future.respond]] which is the equivalent Scala API for further details.
 * @see [[Future.addEventListener]] for using it with a `Future`.
 * @see [[FutureTransformer]] for a Java API for transforming the results of Futures.
 */
trait FutureEventListener[T] {

  /**
   * A side-effect which is invoked if the computation completes successfully.
   */
  def onSuccess(value: T): Unit

  /**
   * A side-effect which is invoked if the computation completes unsuccessfully.
   */
  def onFailure(cause: Throwable): Unit
}
