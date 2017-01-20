package com.twitter.util.logging

import org.slf4j.{LoggerFactory, Marker}
import scala.language.implicitConversions

/**
 * A scala-friendly Logging trait which:
 *
 *  - lazily provides a logger named according to the class in which the trait is mixed
 *  - convenience methods to proxy calls to the lazily provided logger. These methods are
 *    explicitly call-by-name as they are mainly intended for use from Scala.
 *
 * {{{
 *    class MyClass extends Logging {
 *      ...
 *      def foo: String = {
 *        info("In foo method")
 *        "Hello, world!"
 *      }
 *    }
 * }}}
 *
 * For more information, see [[https://https://github.com/twitter/util/blob/develop/util-slf4j-api/README.md util-slf4j-api/README.md]]
 *
 * @define isLevelEnabled
 *
 * Determines if the named log level is enabled on the underlying logger. Returns
 * `true` if enabled, `false` otherwise.
 *
 * @define isLevelEnabledMarker
 *
 * Determines if the named log level is enabled on the underlying logger taking into
 * consideration the given [[Marker]] data. Returns `true` if enabled, `false` otherwise.
 *
 * @define log
 *
 * Logs the given message at the named log level using call-by-name for
 * the message parameter with the underlying logger.
 *
 * @define logMarker
 *
 * Logs the given message at the named log level with the underlying logger taking into
 * consideration the given [[Marker]] data.
 *
 * @define logResult
 *
 * Log the given message at the named log level formatted with the result of the
 * passed in function using the underlying logger. The incoming string message should
 * contain a single `%s` which will be replaced with the result[T] of the given function.
 *
 * Example:
 *
 * {{{
 *   infoResult("The answer is: %s") {"42"}
 * }}}
 */
trait Logging {

  private[this] lazy val _logger: Logger =
    Logger(LoggerFactory.getLogger(getClass))

  /**
   * Return the underlying [[com.twitter.util.logging.Logger]]
   * @return a [[com.twitter.util.logging.Logger]]
   */
  protected[this] def logger: Logger = _logger

  /**
   * Return the name of the underlying [[com.twitter.util.logging.Logger]]
   * @return a String name
   */
  protected def loggerName = logger.name

  /* Trace */

  /** $isLevelEnabled */
  protected[this] def isTraceEnabled: Boolean = logger.isTraceEnabled

  /** $isLevelEnabledMarker */
  protected[this] def isTraceEnabled(marker: Marker): Boolean =
    logger.isTraceEnabled(marker)

  /** $log */
  protected[this] def trace(message: => Any): Unit =
    if (isTraceEnabled) logger.trace(message)

  /** $logMarker */
  protected[this] def trace(marker: Marker, message: => Any): Unit =
    if (isTraceEnabled) logger.trace(marker, message)

  /** $log */
  protected[this] def trace(message: => Any, cause: Throwable): Unit =
    if (isTraceEnabled) logger.trace(message, cause)

  /** $logMarker */
  protected[this] def trace(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (isTraceEnabled) logger.trace(marker, message, cause)

  /** $logResult */
  protected[this] def traceResult[T](message: => String)(fn: => T): T = {
    val result = fn
    trace(message.format(result))
    result
  }

  /* Debug */

  /** $isLevelEnabled */
  protected[this] def isDebugEnabled: Boolean = logger.isDebugEnabled

  /** $isLevelEnabledMarker */
  protected[this] def isDebugEnabled(marker: Marker): Boolean =
    logger.isDebugEnabled(marker)

  /** $log */
  protected[this] def debug(message: => Any): Unit =
    if (isDebugEnabled) logger.debug(message)

  /** $logMarker */
  protected[this] def debug(marker: Marker, message: => Any): Unit =
    if (isDebugEnabled) logger.debug(marker, message)

  /** $log */
  protected[this] def debug(message: => Any, cause: Throwable): Unit =
    if (isDebugEnabled) logger.debug(message, cause)

  /** $logMarker */
  protected[this] def debug(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (isDebugEnabled) logger.debug(marker, message, cause)

  /** $logResult */
  protected[this] def debugResult[T](message: => String)(fn: => T): T = {
    val result = fn
    debug(message.format(result))
    result
  }

  /* Info */

  /** $isLevelEnabled */
  protected[this] def isInfoEnabled: Boolean = logger.isInfoEnabled

  /** $isLevelEnabledMarker */
  protected[this] def isInfoEnabled(marker: Marker): Boolean =
    logger.isInfoEnabled(marker)

  /** $log */
  protected[this] def info(message: => Any): Unit =
    if (isInfoEnabled) logger.info(message)

  /** $logMarker */
  protected[this] def info(marker: Marker, message: => Any): Unit =
    if (isInfoEnabled) logger.info(marker, message)

  /** $log */
  protected[this] def info(message: => Any, cause: Throwable): Unit =
    if (isInfoEnabled) logger.info(message, cause)

  /** $logMarker */
  protected[this] def info(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (isInfoEnabled) logger.info(marker, message, cause)

  /** $logResult */
  protected[this] def infoResult[T](message: => String)(fn: => T): T = {
    val result = fn
    info(message.format(result))
    result
  }

  /* Warn */

  /** $isLevelEnabled */
  protected[this] def isWarnEnabled: Boolean = logger.isWarnEnabled

  /** $isLevelEnabledMarker */
  protected[this] def isWarnEnabled(marker: Marker): Boolean =
    logger.isWarnEnabled(marker)

  /** $log */
  protected[this] def warn(message: => Any): Unit =
    if (isWarnEnabled) logger.warn(message)

  /** $logMarker */
  protected[this] def warn(marker: Marker, message: => Any): Unit =
    if (isWarnEnabled) logger.warn(marker, message)

  /** $log */
  protected[this] def warn(message: => Any, cause: Throwable): Unit =
    if (isWarnEnabled) logger.warn(message, cause)

  /** $logMarker */
  protected[this] def warn(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (isWarnEnabled) logger.warn(marker, message, cause)

  /** $logResult */
  protected[this] def warnResult[T](message: => String)(fn: => T): T = {
    val result = fn
    warn(message.format(result))
    result
  }

  /* Error */

  /** $isLevelEnabled */
  protected[this] def isErrorEnabled: Boolean = logger.isErrorEnabled

  /** $isLevelEnabledMarker */
  protected[this] def isErrorEnabled(marker: Marker): Boolean =
    logger.isErrorEnabled(marker)

  /** $log */
  protected[this] def error(message: => Any): Unit =
    if (isErrorEnabled) logger.error(message)

  /** $log */
  protected[this] def error(message: => Any, cause: Throwable): Unit =
    if (isErrorEnabled) logger.error(message, cause)

  /** $logMarker */
  protected[this] def error(marker: Marker, message: => Any): Unit =
    if (isErrorEnabled) logger.error(marker, message)

  /** $logMarker */
  protected[this] def error(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (isErrorEnabled) logger.error(marker, message, cause)

  /** $logResult */
  protected[this] def errorResult[T](message: => String)(fn: => T): T = {
    val result = fn
    error(message.format(result))
    result
  }

  /* Private */

  /**
   * Implicitly convert [[Any]] type to string to safely deal with nulls
   * in the above functions.
   */
  private[this] implicit def anyToString(obj: Any): String = obj match {
    case null => "null"
    case _ => obj.toString
  }
}
