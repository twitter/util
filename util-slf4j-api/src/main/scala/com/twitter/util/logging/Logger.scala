package com.twitter.util.logging

import org.slf4j
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import scala.reflect.ClassTag
import scala.reflect.classTag

/**
 * Companion object for [[com.twitter.util.logging.Logger]] which provides
 * factory methods for creation.
 *
 * @define ApplyScaladocLink
 * [[com.twitter.util.logging.Logger.apply(name:String):com\.twitter\.util\.logging\.Logger* apply(String)]]
 *
 * @define ApplyLoggerScaladocLink
 * [[com.twitter.util.logging.Logger.apply(underlying:org\.slf4j\.Logger):com\.twitter\.util\.logging\.Logger* apply(Logger)]]
 *
 * @define ApplyClassScaladocLink
 * [[com.twitter.util.logging.Logger.apply(clazz:Class[_]):com\.twitter\.util\.logging\.Logger* apply(Class)]]
 *
 * @define GetLoggerScaladocLink
 * [[com.twitter.util.logging.Logger.getLogger(name:String):com\.twitter\.util\.logging\.Logger* getLogger(String)]]
 *
 * @define GetLoggerClassScaldocLink
 * [[com.twitter.util.logging.Logger.getLogger(clazz:Class[_]):com\.twitter\.util\.logging\.Logger* getLogger(Class)]]
 *
 * @define GetLoggerLoggerScaladocLink
 * [[com.twitter.util.logging.Logger.getLogger(underlying:org\.slf4j\.Logger):com\.twitter\.util\.logging\.Logger* getLogger(Logger)]]
 */
object Logger {

  /**
   * Create a [[com.twitter.util.logging.Logger]] for the given name.
   * @param name name of the underlying `org.slf4j.Logger`.
   * @see Java users see $GetLoggerScaladocLink
   *
   * {{{
   *    val logger = Logger("name")
   * }}}
   */
  def apply(name: String): Logger = {
    new Logger(LoggerFactory.getLogger(name))
  }

  /**
   * Create a [[Logger]] for the given name.
   * @param name name of the underlying `org.slf4j.Logger`.
   * @see Scala users see $ApplyScaladocLink
   *
   * {{{
   *    Logger logger = Logger.getLogger("name");
   * }}}
   */
  def getLogger(name: String): Logger =
    Logger(name)

  /**
   * Create a [[com.twitter.util.logging.Logger]] named for the given class.
   * @param clazz class to use for naming the underlying Logger.
   * @note Scala singleton object classes are converted to loggers named without the `$` suffix.
   * @see Java users see $GetLoggerClassScaladocLink
   * @see [[https://docs.scala-lang.org/tour/singleton-objects.html]]
   *
   * {{{
   *    val logger = Logger(classOf[MyClass])
   * }}}
   */
  def apply(clazz: Class[_]): Logger = {
    if (isSingletonObject(clazz))
      new Logger(LoggerFactory.getLogger(objectClazzName(clazz)))
    else new Logger(LoggerFactory.getLogger(clazz))
  }

  /**
   * Create a [[Logger]] named for the given class.
   * @param clazz class to use for naming the underlying `org.slf4j.Logger`.
   * @see Scala users see $ApplyClassScaladocLink
   *
   * {{{
   *    Logger logger = Logger.getLogger(MyClass.class);
   * }}}
   */
  def getLogger(clazz: Class[_]): Logger =
    Logger(clazz)

  /**
   * Create a [[com.twitter.util.logging.Logger]] for the runtime class wrapped
   * by the implicit `scala.reflect.ClassTag` denoting the runtime class `T`.
   * @tparam T the runtime class type
   * @note Scala singleton object classes are converted to loggers named without the `$` suffix.
   * @see [[https://docs.scala-lang.org/tour/singleton-objects.html]]
   *
   * {{{
   *    val logger = Logger[MyClass]
   * }}}
   */
  def apply[T: ClassTag]: Logger = {
    val clazz = classTag[T].runtimeClass
    if (isSingletonObject(clazz))
      new Logger(LoggerFactory.getLogger(objectClazzName(clazz)))
    else new Logger(LoggerFactory.getLogger(clazz))
  }

  /**
   * Create a [[com.twitter.util.logging.Logger]] wrapping the given underlying
   * `org.slf4j.Logger`.
   * @param underlying an `org.slf4j.Logger`
   * @see Java users see $GetLoggerLoggerScaladocLink
   *
   * {{{
   *    val logger = Logger(LoggerFactory.getLogger("name"))
   * }}}
   */
  def apply(underlying: slf4j.Logger): Logger = {
    new Logger(underlying)
  }

  /**
   * Create a [[Logger]] wrapping the given underlying
   * `org.slf4j.Logger`.
   * @param underlying an `org.slf4j.Logger`
   * @see Scala users see $ApplyLoggerScaladocLink
   *
   * {{{
   *    Logger logger = Logger.getLogger(LoggerFactory.getLogger("name"));
   * }}}
   */
  def getLogger(underlying: slf4j.Logger): Logger =
    Logger(underlying)

  /**
   * Returns true if the given instance is a Scala singleton object, false otherwise.
   * EXPOSED FOR TESTING
   * @see [[https://docs.scala-lang.org/tour/singleton-objects.html]]
   */
  private[logging] def isSingletonObject(clazz: Class[_]): Boolean = {
    // While Scala's reflection utilities offer this in a succinct and convenient
    // method, the startup costs are quite high and in testing, the results not always
    // accurate. The approach used here, while fragile to Scala internal changes, was
    // deemed worth the tradeoff. This assumes that singleton objects have class names that
    // end with $ and have a field on them called MODULE$. For example `object Foo`
    // has the class name `Foo` and a `MODULE$` field.
    // This works as of Scala 2.12, 2.13 and 3.0.2-RC1.
    val className = clazz.getName
    if (!className.endsWith("$"))
      return false

    try {
      clazz.getField("MODULE$") // this throws if the field doesn't exist
      true
    } catch {
      case _: NoSuchFieldException => false
    }
  }

  // EXPOSED FOR TESTING
  private[logging] def objectClazzName(clazz: Class[_]) = clazz.getName.stripSuffix("$")
}

/**
 * A scala wrapper over a `org.slf4j.Logger`.
 *
 * The Logger extends [[https://docs.oracle.com/javase/8/docs/api/java/io/Serializable.html java.io.Serializable]]
 * to support it's usage through the [[com.twitter.util.logging.Logging]] trait when the trait is mixed
 * into a [[https://docs.oracle.com/javase/8/docs/api/java/io/Serializable.html java.io.Serializable]] class.
 *
 * Note, logging methods have a call-by-name variation which are intended for use from Scala.
 *
 * @define isLevelEnabled
 *
 * Determines if the named log level is enabled. Returns `true` if enabled, `false` otherwise.
 *
 * @define isLevelEnabledMarker
 *
 * Determines if the named log level is enabled taking into consideration the given
 * `org.slf4j.Marker` data. Returns `true` if enabled, `false` otherwise.
 *
 * @define log
 *
 * Logs the given message at the named log level.
 *
 * @define logMarker
 *
 * Logs the given message at the named log level taking into consideration the
 * given `org.slf4j.Marker` data.
 *
 * @define logWith
 *
 * Log the given parameterized message at the named log level using the given
 * args. See [[https://www.slf4j.org/faq.html#logging_performance Parameterized Message Logging]]
 *
 * @define logWithMarker
 *
 * Log the given parameterized message at the named log level using the given
 * args taking into consideration the given `org.slf4j.Marker` data. See [[https://www.slf4j.org/faq.html#logging_performance Parameterized Message Logging]]
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
@SerialVersionUID(1L)
final class Logger private (val underlying: slf4j.Logger) extends Serializable {

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
  def trace(message: => Any): Unit =
    if (underlying.isTraceEnabled) underlying.trace(toString(message))

  /** $log */
  def trace(message: String, cause: Throwable): Unit =
    if (underlying.isTraceEnabled) underlying.trace(message, cause)

  /** $log */
  def trace(message: => Any, cause: Throwable): Unit =
    if (underlying.isTraceEnabled) underlying.trace(toString(message), cause)

  /** $logMarker */
  def trace(marker: Marker, message: String): Unit =
    if (underlying.isTraceEnabled(marker)) underlying.trace(marker, message)

  /** $logMarker */
  def trace(marker: Marker, message: => Any): Unit =
    if (underlying.isTraceEnabled(marker)) underlying.trace(marker, toString(message))

  /** $logMarker */
  def trace(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isTraceEnabled(marker)) underlying.trace(marker, message, cause)

  /** $logMarker */
  def trace(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (underlying.isTraceEnabled(marker)) underlying.trace(marker, toString(message), cause)

  /** $logWith */
  def traceWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isTraceEnabled) underlying.trace(message)
    } else {
      if (underlying.isTraceEnabled) underlying.trace(message, args: _*)
    }

  /** $logWith */
  def traceWith(message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isTraceEnabled) underlying.trace(toString(message))
    } else {
      if (underlying.isTraceEnabled) underlying.trace(toString(message), args: _*)
    }

  /** $logWithMarker */
  def traceWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isTraceEnabled(marker)) underlying.trace(marker, message)
    } else {
      if (underlying.isTraceEnabled(marker)) underlying.trace(marker, message, args: _*)
    }

  /** $logWithMarker */
  def traceWith(marker: Marker, message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isTraceEnabled(marker)) underlying.trace(marker, toString(message))
    } else {
      if (underlying.isTraceEnabled(marker)) underlying.trace(marker, toString(message), args: _*)
    }

  /** $logResult */
  def traceResult[T](message: => String)(fn: => T): T = {
    val result = fn
    trace(message.format(result))
    result
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
  def debug(message: => Any): Unit =
    if (underlying.isDebugEnabled) underlying.debug(toString(message))

  /** $log */
  def debug(message: String, cause: Throwable): Unit =
    if (underlying.isDebugEnabled) underlying.debug(message, cause)

  /** $log */
  def debug(message: => Any, cause: Throwable): Unit =
    if (underlying.isDebugEnabled) underlying.debug(toString(message), cause)

  /** $logMarker */
  def debug(marker: Marker, message: String): Unit =
    if (underlying.isDebugEnabled(marker)) underlying.debug(marker, message)

  /** $logMarker */
  def debug(marker: Marker, message: => Any): Unit =
    if (underlying.isDebugEnabled(marker)) underlying.debug(marker, toString(message))

  /** $logMarker */
  def debug(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isDebugEnabled(marker)) underlying.debug(marker, message, cause)

  /** $logMarker */
  def debug(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (underlying.isDebugEnabled(marker)) underlying.debug(marker, toString(message), cause)

  /** $logWith */
  def debugWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isDebugEnabled) underlying.debug(message)
    } else {
      if (underlying.isDebugEnabled) underlying.debug(message, args: _*)
    }

  /** $logWith */
  def debugWith(message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isDebugEnabled) underlying.debug(toString(message))
    } else {
      if (underlying.isDebugEnabled) underlying.debug(toString(message), args: _*)
    }

  /** $logWithMarker */
  def debugWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isDebugEnabled(marker)) underlying.debug(marker, message)
    } else {
      if (underlying.isDebugEnabled(marker)) underlying.debug(marker, message, args: _*)
    }

  /** $logWithMarker */
  def debugWith(marker: Marker, message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isDebugEnabled(marker)) underlying.debug(marker, toString(message))
    } else {
      if (underlying.isDebugEnabled(marker)) underlying.debug(marker, toString(message), args: _*)
    }

  /** $logResult */
  def debugResult[T](message: => String)(fn: => T): T = {
    val result = fn
    debug(message.format(result))
    result
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
  def info(message: => Any): Unit =
    if (underlying.isInfoEnabled) underlying.info(toString(message))

  /** $log */
  def info(message: String, cause: Throwable): Unit =
    if (underlying.isInfoEnabled) underlying.info(message, cause)

  /** $log */
  def info(message: => Any, cause: Throwable): Unit =
    if (underlying.isInfoEnabled) underlying.info(toString(message), cause)

  /** $logMarker */
  def info(marker: Marker, message: String): Unit =
    if (underlying.isInfoEnabled(marker)) underlying.info(marker, message)

  /** $logMarker */
  def info(marker: Marker, message: => Any): Unit =
    if (underlying.isInfoEnabled(marker)) underlying.info(marker, toString(message))

  /** $logMarker */
  def info(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isInfoEnabled(marker)) underlying.info(marker, message, cause)

  /** $logMarker */
  def info(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (underlying.isInfoEnabled(marker)) underlying.info(marker, toString(message), cause)

  /** $logWith */
  def infoWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isInfoEnabled) underlying.info(message)
    } else {
      if (underlying.isInfoEnabled) underlying.info(message, args: _*)
    }

  /** $logWith */
  def infoWith(message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isInfoEnabled) underlying.info(toString(message))
    } else {
      if (underlying.isInfoEnabled) underlying.info(toString(message), args: _*)
    }

  /** $logWithMarker */
  def infoWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isInfoEnabled(marker)) underlying.info(marker, message)
    } else {
      if (underlying.isInfoEnabled(marker)) underlying.info(marker, message, args: _*)
    }

  /** $logWithMarker */
  def infoWith(marker: Marker, message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isInfoEnabled(marker)) underlying.info(marker, toString(message))
    } else {
      if (underlying.isInfoEnabled(marker)) underlying.info(marker, toString(message), args: _*)
    }

  /** $logResult */
  def infoResult[T](message: => String)(fn: => T): T = {
    val result = fn
    info(message.format(result))
    result
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
  def warn(message: => Any): Unit =
    if (underlying.isWarnEnabled) underlying.warn(toString(message))

  /** $log */
  def warn(message: String, cause: Throwable): Unit =
    if (underlying.isWarnEnabled) underlying.warn(message, cause)

  /** $log */
  def warn(message: => Any, cause: Throwable): Unit =
    if (underlying.isWarnEnabled) underlying.warn(toString(message), cause)

  /** $logMarker */
  def warn(marker: Marker, message: String): Unit =
    if (underlying.isWarnEnabled(marker)) underlying.warn(marker, message)

  /** $logMarker */
  def warn(marker: Marker, message: => Any): Unit =
    if (underlying.isWarnEnabled(marker)) underlying.warn(marker, toString(message))

  /** $logMarker */
  def warn(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isWarnEnabled(marker)) underlying.warn(marker, message, cause)

  /** $logMarker */
  def warn(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (underlying.isWarnEnabled(marker)) underlying.warn(marker, toString(message), cause)

  /** $logWith */
  def warnWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isWarnEnabled) underlying.warn(message)
    } else {
      if (underlying.isWarnEnabled) underlying.warn(message, args: _*)
    }

  /** $logWith */
  def warnWith(message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isWarnEnabled) underlying.warn(toString(message))
    } else {
      if (underlying.isWarnEnabled) underlying.warn(toString(message), args: _*)
    }

  /** $logWithMarker */
  def warnWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isWarnEnabled(marker)) underlying.warn(marker, message)
    } else {
      if (underlying.isWarnEnabled(marker)) underlying.warn(marker, message, args: _*)
    }

  /** $logWithMarker */
  def warnWith(marker: Marker, message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isWarnEnabled(marker)) underlying.warn(marker, toString(message))
    } else {
      if (underlying.isWarnEnabled(marker)) underlying.warn(marker, toString(message), args: _*)
    }

  /** $logResult */
  def warnResult[T](message: => String)(fn: => T): T = {
    val result = fn
    warn(message.format(result))
    result
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
  def error(message: => Any): Unit =
    if (underlying.isErrorEnabled) underlying.error(toString(message))

  /** $log */
  def error(message: String, cause: Throwable): Unit =
    if (underlying.isErrorEnabled) underlying.error(message, cause)

  /** $log */
  def error(message: => Any, cause: Throwable): Unit =
    if (underlying.isErrorEnabled) underlying.error(toString(message), cause)

  /** $logMarker */
  def error(marker: Marker, message: String): Unit =
    if (underlying.isErrorEnabled(marker)) underlying.error(marker, message)

  /** $logMarker */
  def error(marker: Marker, message: => Any): Unit =
    if (underlying.isErrorEnabled(marker)) underlying.error(marker, toString(message))

  /** $logMarker */
  def error(marker: Marker, message: String, cause: Throwable): Unit =
    if (underlying.isErrorEnabled(marker)) underlying.error(marker, message, cause)

  /** $logMarker */
  def error(marker: Marker, message: => Any, cause: Throwable): Unit =
    if (underlying.isErrorEnabled(marker)) underlying.error(marker, toString(message), cause)

  /** $logWith */
  def errorWith(message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isErrorEnabled) underlying.error(message)
    } else {
      if (underlying.isErrorEnabled) underlying.error(message, args: _*)
    }

  /** $logWith */
  def errorWith(message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isErrorEnabled) underlying.error(toString(message))
    } else {
      if (underlying.isErrorEnabled) underlying.error(toString(message), args: _*)
    }

  /** $logWithMarker */
  def errorWith(marker: Marker, message: String, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isErrorEnabled(marker)) underlying.error(marker, message)
    } else {
      if (underlying.isErrorEnabled(marker)) underlying.error(marker, message, args: _*)
    }

  /** $logWithMarker */
  def errorWith(marker: Marker, message: => Any, args: AnyRef*): Unit =
    if (args.isEmpty) {
      if (underlying.isErrorEnabled(marker)) underlying.error(marker, toString(message))
    } else {
      if (underlying.isErrorEnabled(marker)) underlying.error(marker, toString(message), args: _*)
    }

  /** $logResult */
  def errorResult[T](message: => String)(fn: => T): T = {
    val result = fn
    error(message.format(result))
    result
  }

  /* Private */

  /**
   * Convert [[Any]] type to [[String]] to safely deal with nulls in the above functions.
   */
  private[this] def toString(obj: Any): String = obj match {
    case null => "null"
    case _ => obj.toString
  }
}
