package com.twitter.util.logging

import org.slf4j
import org.slf4j.{LoggerFactory, Marker}
import scala.reflect.ClassTag

/**
 * Companion object for [[com.twitter.util.logging.Logger]] which provides
 * factory methods for creation.
 */
object Logger {

  /**
   * Create a [[com.twitter.util.logging.Logger]] for the given name.
   * @param name - name of the underlying Logger.
   *
   * {{{
   *    val logger = Logger("name")
   * }}}
   */
  def apply(name: String): Logger = {
    new Logger(LoggerFactory.getLogger(name))
  }

  /**
   * Create a [[com.twitter.util.logging.Logger]] named for the given class.
   * @param clazz - class to use for naming the underlying Logger.
   *
   * {{{
   *    val logger = Logger(classOf[MyClass])
   * }}}
   */
  def apply(clazz: Class[_]): Logger = {
    new Logger(LoggerFactory.getLogger(clazz))
  }

  /**
   * Create a [[com.twitter.util.logging.Logger]] for the runtime class wrapped
   * by the implicit class tag.
   * @param classTag - the [[scala.reflect.ClassTag]] denoting the runtime class `T`.
   * @tparam T - the runtime class type
   *
   * {{{
   *    val logger = Logger[MyClass]
   * }}}
   */
  def apply[T](implicit classTag: ClassTag[T]): Logger = {
    new Logger(LoggerFactory.getLogger(classTag.runtimeClass))
  }

  /**
   * Create a [[com.twitter.util.logging.Logger]] wrapping the given underlying
   * [[org.slf4j.Logger]].
   * @param underlying - an `org.slf4j.Logger`
   *
   * {{{
   *    val logger = Logger(LoggerFactory.getLogger("name"))
   * }}}
   */
  def apply(underlying: slf4j.Logger): Logger = {
    new Logger(underlying)
  }
}

/**
 * A scala wrapper over a [[org.slf4j.Logger]].
 *
 * The Logger is [[Serializable]] to support it's usage through the
 * [[com.twitter.util.logging.Logging]] trait when the trait is mixed
 * into a [[Serializable]] class.
 *
 * @define isLevelEnabled
 *
 * Determines if the named log level is enabled. Returns `true` if enabled, `false` otherwise.
 *
 * @define isLevelEnabledMarker
 *
 * Determines if the named log level is enabled taking into consideration the given [[Marker]] data.
 * Returns `true` if enabled, `false` otherwise.
 *
 * @define log
 *
 * Logs the given message at the named log level.
 *
 * @define logMarker
 *
 * Logs the given message at the named log level taking into consideration the
 * given [[Marker]] data.
 *
 * @define logWith
 *
 * Log the given parameterized message at the named log level using the given
 * args. See [[http://www.slf4j.org/faq.html#logging_performance Parameterized Message Logging]]
 *
 * @define logWithMarker
 *
 * Log the given parameterized message at the named log level using the given
 * args taking into consideration the given [[Marker]] data.
 * See [[http://www.slf4j.org/faq.html#logging_performance Parameterized Message Logging]]
 */
@SerialVersionUID(1L)
final class Logger private (underlying: slf4j.Logger) extends Serializable {

  /* NOTE: we do not expose the non-varargs variations as we are unable to
     correctly call them from Scala, i.e.,

     def info(var1: String, var2: Object)
     def info(var1: String, var2: Object, var3: Object)

     due to: https://issues.scala-lang.org/browse/SI-4775 */

  def name: String = underlying.getName

  /* Trace */

  /** $isLevelEnabled */
  def isTraceEnabled: Boolean = underlying.isTraceEnabled()

  /** $isLevelEnabledMarker */
  def isTraceEnabled(marker: Marker): Boolean =
    underlying.isTraceEnabled(marker)

  /** $log */
  def trace(message: String): Unit =
    if (underlying.isTraceEnabled) underlying.trace(message)

  /** $log */
  def trace(message: String, cause: Throwable): Unit =
    if (underlying.isTraceEnabled) underlying.trace(message, cause)

  /** $logMarker */
  def trace(marker: Marker, message: String): Unit =
    if (underlying.isTraceEnabled) underlying.trace(marker, message)

  /** $logMarker */
  def trace(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isTraceEnabled) underlying.trace(marker, message, cause)

  /** $logWith */
  def traceWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isTraceEnabled) underlying.trace(message)
    } else {
      if (underlying.isTraceEnabled) underlying.trace(message, args: _*)
    }

  /** $logWithMarker */
  def traceWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isTraceEnabled) underlying.trace(marker, message)
    } else {
      if (underlying.isTraceEnabled) underlying.trace(marker, message, args: _*)
    }

  /* Debug */

  /** $isLevelEnabled */
  def isDebugEnabled: Boolean = underlying.isDebugEnabled()

  /** $isLevelEnabledMarker */
  def isDebugEnabled(marker: Marker): Boolean =
    underlying.isDebugEnabled(marker)

  /** $log */
  def debug(message: String): Unit =
    if (underlying.isDebugEnabled) underlying.debug(message)

  /** $log */
  def debug(message: String, cause: Throwable): Unit =
    if (underlying.isDebugEnabled) underlying.debug(message, cause)

  /** $logMarker */
  def debug(marker: Marker, message: String): Unit =
    if (underlying.isDebugEnabled) underlying.debug(marker, message)

  /** $logMarker */
  def debug(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isDebugEnabled) underlying.debug(marker, message, cause)

  /** $logWith */
  def debugWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isDebugEnabled) underlying.debug(message)
    } else {
      if (underlying.isDebugEnabled) underlying.debug(message, args: _*)
    }

  /** $logWithMarker */
  def debugWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isDebugEnabled) underlying.debug(marker, message)
    } else {
      if (underlying.isDebugEnabled) underlying.debug(marker, message, args: _*)
    }

  /* Info */

  /** $isLevelEnabled */
  def isInfoEnabled: Boolean = underlying.isInfoEnabled()

  /** $isLevelEnabledMarker */
  def isInfoEnabled(marker: Marker): Boolean =
    underlying.isInfoEnabled(marker)

  /** $log */
  def info(message: String): Unit =
    if (underlying.isInfoEnabled) underlying.info(message)

  /** $log */
  def info(message: String, cause: Throwable): Unit =
    if (underlying.isInfoEnabled) underlying.info(message, cause)

  /** $logMarker */
  def info(marker: Marker, message: String): Unit =
    if (underlying.isInfoEnabled) underlying.info(marker, message)

  /** $logMarker */
  def info(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isInfoEnabled) underlying.info(marker, message, cause)

  /** $logWith */
  def infoWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isInfoEnabled) underlying.info(message)
    } else {
      if (underlying.isInfoEnabled) underlying.info(message, args: _*)
    }

  /** $logWithMarker */
  def infoWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isInfoEnabled) underlying.info(marker, message)
    } else {
      if (underlying.isInfoEnabled) underlying.info(marker, message, args: _*)
    }

  /* Warn */

  /** $isLevelEnabled */
  def isWarnEnabled: Boolean = underlying.isWarnEnabled()

  /** $isLevelEnabledMarker */
  def isWarnEnabled(marker: Marker): Boolean =
    underlying.isWarnEnabled(marker)

  /** $log */
  def warn(message: String): Unit =
    if (underlying.isWarnEnabled) underlying.warn(message)

  /** $log */
  def warn(message: String, cause: Throwable): Unit =
    if (underlying.isWarnEnabled) underlying.warn(message, cause)

  /** $logMarker */
  def warn(marker: Marker, message: String): Unit =
    if (underlying.isWarnEnabled) underlying.warn(marker, message)

  /** $logMarker */
  def warn(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isWarnEnabled) underlying.warn(marker, message, cause)

  /** $logWith */
  def warnWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isWarnEnabled) underlying.warn(message)
    } else {
      if (underlying.isWarnEnabled) underlying.warn(message, args: _*)
    }

  /** $logWithMarker */
  def warnWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isWarnEnabled) underlying.warn(marker, message)
    } else {
      if (underlying.isWarnEnabled) underlying.warn(marker, message, args: _*)
    }

  /* Error */

  /** $isLevelEnabled */
  def isErrorEnabled: Boolean = underlying.isErrorEnabled()

  /** $isLevelEnabledMarker */
  def isErrorEnabled(marker: Marker): Boolean =
    underlying.isErrorEnabled(marker)

  /** $log */
  def error(message: String): Unit =
    if (underlying.isErrorEnabled) underlying.error(message)

  /** $log */
  def error(message: String, cause: Throwable): Unit =
    if (underlying.isErrorEnabled) underlying.error(message, cause)

  /** $logMarker */
  def error(marker: Marker, message: String): Unit =
    if (underlying.isErrorEnabled) underlying.error(marker, message)

  /** $logMarker */
  def error(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isErrorEnabled) underlying.error(marker, message, cause)

  /** $logWith */
  def errorWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isErrorEnabled) underlying.error(message)
    } else {
      if (underlying.isErrorEnabled) underlying.error(message, args: _*)
    }

  /** $logWithMarker */
  def errorWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isErrorEnabled) underlying.error(marker, message)
    } else {
      if (underlying.isErrorEnabled) underlying.error(marker, message, args: _*)
    }
}
