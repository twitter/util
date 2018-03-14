package com.twitter.app

import scala.util.control.NoStackTrace

/**
 * An exception that represents collected errors which occurred on close of the app.
 *
 * @note When execution of the `App#nonExitingMain` throws a [[CloseException]], the app will not
 *       attempt to call `App#close()` again in the `App#exitOnError(t: Throwable)` function since
 *       this Exception is assumed be a result of already calling `App#close()`.
 *
 * @note Collected exceptions which occurred during closing are added as "suppressed" exceptions.
 *
 * @see [[https://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html#getSuppressed()]]
 */
final class CloseException private[twitter] (message: String)
    extends Exception(message)
    with NoStackTrace
