/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

import java.io.{File, InputStream}
import java.util.{Calendar, logging => javalog}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.{JavaConversions, Map}
import scala.collection.mutable
import scala.io.Source
import config._

// replace java's ridiculous log levels with the standard ones.
sealed abstract class Level(val name: String, val value: Int) extends javalog.Level(name, value) {
  Logger.levelNamesMap(name) = this
  Logger.levelsMap(value) = this
}

object Level {
  case object OFF extends Level("OFF", Int.MaxValue)
  case object FATAL extends Level("FATAL", 1000)
  case object CRITICAL extends Level("CRITICAL", 970)
  case object ERROR extends Level("ERROR", 930)
  case object WARNING extends Level("WARNING", 900)
  case object INFO extends Level("INFO", 800)
  case object DEBUG extends Level("DEBUG", 500)
  case object TRACE extends Level("TRACE", 400)
  case object ALL extends Level("ALL", Int.MinValue)

  // to force them to get loaded from class files:
  {
    val root = Logger.root
    val originalLevel = root.getLevel()
    root.setLevel(OFF)
    root.setLevel(FATAL)
    root.setLevel(CRITICAL)
    root.setLevel(ERROR)
    root.setLevel(WARNING)
    root.setLevel(INFO)
    root.setLevel(DEBUG)
    root.setLevel(TRACE)
    root.setLevel(ALL)
    root.setLevel(originalLevel)
  }
}

class LoggingException(reason: String) extends Exception(reason)

/**
 * Scala wrapper for logging.
 */
class Logger private(val name: String, private val wrapped: javalog.Logger) {
  // wrapped methods:
  def addHandler(handler: javalog.Handler) = wrapped.addHandler(handler)
  def getFilter() = wrapped.getFilter()
  def getHandlers() = wrapped.getHandlers()
  def getLevel() = wrapped.getLevel()
  def getParent() = wrapped.getParent()
  def getUseParentHandlers() = wrapped.getUseParentHandlers()
  def isLoggable(level: javalog.Level) = wrapped.isLoggable(level)
  def log(record: javalog.LogRecord) = wrapped.log(record)
  def removeHandler(handler: javalog.Handler) = wrapped.removeHandler(handler)
  def setFilter(filter: javalog.Filter) = wrapped.setFilter(filter)
  def setLevel(level: javalog.Level) = wrapped.setLevel(level)
  def setUseParentHandlers(use: Boolean) = wrapped.setUseParentHandlers(use)

  override def toString = {
    "<%s name='%s' level=%s handlers=%s use_parent=%s>".format(getClass.getName, name, getLevel(),
      getHandlers().toList.mkString("[", ", ", "]"), if (getUseParentHandlers()) "true" else "false")
  }

  /**
   * Log a message, with sprintf formatting, at the desired level.
   */
  final def log(level: Level, message: String, items: Any*): Unit =
    log(level, null: Throwable, message, items: _*)

  /**
   * Log a message, with sprintf formatting, at the desired level, and
   * attach an exception and stack trace.
   */
  final def log(level: Level, thrown: Throwable, message: String, items: Any*) {
    val myLevel = getLevel
    if ((myLevel eq null) || (level.intValue >= myLevel.intValue)) {
      val record = new javalog.LogRecord(level, message)
      if (items.size > 0) {
        record.setParameters(items.toArray[Any].asInstanceOf[Array[AnyRef]])
      }
      record.setLoggerName(wrapped.getName)
      if (thrown ne null) {
        record.setThrown(thrown)
      }
      wrapped.log(record)
    }
  }

  final def apply(level: Level, message: String, items: Any*) = log(level, message, items)

  final def apply(level: Level, thrown: Throwable, message: String, items: Any*) = log(level, thrown, message, items)

  // convenience methods:
  def fatal(msg: String, items: Any*) = log(Level.FATAL, msg, items: _*)
  def fatal(thrown: Throwable, msg: String, items: Any*) = log(Level.FATAL, thrown, msg, items: _*)
  def critical(msg: String, items: Any*) = log(Level.CRITICAL, msg, items: _*)
  def critical(thrown: Throwable, msg: String, items: Any*) = log(Level.CRITICAL, thrown, msg, items: _*)
  def error(msg: String, items: Any*) = log(Level.ERROR, msg, items: _*)
  def error(thrown: Throwable, msg: String, items: Any*) = log(Level.ERROR, thrown, msg, items: _*)
  def warning(msg: String, items: Any*) = log(Level.WARNING, msg, items: _*)
  def warning(thrown: Throwable, msg: String, items: Any*) = log(Level.WARNING, thrown, msg, items: _*)
  def info(msg: String, items: Any*) = log(Level.INFO, msg, items: _*)
  def info(thrown: Throwable, msg: String, items: Any*) = log(Level.INFO, thrown, msg, items: _*)
  def debug(msg: String, items: Any*) = log(Level.DEBUG, msg, items: _*)
  def debug(thrown: Throwable, msg: String, items: Any*) = log(Level.DEBUG, thrown, msg, items: _*)
  def trace(msg: String, items: Any*) = log(Level.TRACE, msg, items: _*)
  def trace(thrown: Throwable, msg: String, items: Any*) = log(Level.TRACE, thrown, msg, items: _*)

  /**
   * Log a message, with lazy (call-by-name) computation of the message,
   * at the desired level.
   */
  def logLazy(level: Level, message: => AnyRef): Unit = logLazy(level, null: Throwable, message)

  /**
   * Log a message, with lazy (call-by-name) computation of the message,
   * and attach an exception and stack trace.
   */
  def logLazy(level: Level, thrown: Throwable, message: => AnyRef): Unit = {
    val myLevel = getLevel
    if ((myLevel eq null) || (level.intValue >= myLevel.intValue)) {
      val record = new LazyLogRecord(level, message)
      record.setLoggerName(wrapped.getName)
      if (thrown ne null) {
        record.setThrown(thrown)
      }
      wrapped.log(record)
    }
  }

  // convenience methods:
  def ifFatal(message: => AnyRef) = logLazy(Level.FATAL, message)
  def ifFatal(thrown: Throwable, message: => AnyRef) = logLazy(Level.FATAL, thrown, message)
  def ifCritical(message: => AnyRef) = logLazy(Level.CRITICAL, message)
  def ifCritical(thrown: Throwable, message: => AnyRef) = logLazy(Level.CRITICAL, thrown, message)
  def ifError(message: => AnyRef) = logLazy(Level.ERROR, message)
  def ifError(thrown: Throwable, message: => AnyRef) = logLazy(Level.ERROR, thrown, message)
  def ifWarning(message: => AnyRef) = logLazy(Level.WARNING, message)
  def ifWarning(thrown: Throwable, message: => AnyRef) = logLazy(Level.WARNING, thrown, message)
  def ifInfo(message: => AnyRef) = logLazy(Level.INFO, message)
  def ifInfo(thrown: Throwable, message: => AnyRef) = logLazy(Level.INFO, thrown, message)
  def ifDebug(message: => AnyRef) = logLazy(Level.DEBUG, message)
  def ifDebug(thrown: Throwable, message: => AnyRef) = logLazy(Level.DEBUG, thrown, message)
  def ifTrace(message: => AnyRef) = logLazy(Level.TRACE, message)
  def ifTrace(thrown: Throwable, message: => AnyRef) = logLazy(Level.TRACE, thrown, message)

  /**
   * Remove all existing log handlers.
   */
  def clearHandlers() = {
    // some custom Logger implementations may return null from getHandlers
    val handlers = getHandlers()
    if (handlers ne null) {
      for (handler <- handlers) {
        try {
          handler.close()
        } catch { case _ => () }
        removeHandler(handler)
      }
    }
  }
}

object Logger extends Iterable[Logger] {
  private[logging] val levelNamesMap = new mutable.HashMap[String, Level]
  private[logging] val levelsMap = new mutable.HashMap[Int, Level]

  // A cache of scala Logger objects by name.
  // Using a low concurrencyLevel (2), with the assumption that we aren't ever creating too
  // many loggers at the same time.
  private val loggersCache = new ConcurrentHashMap[String, Logger](128, 0.75f, 2)

  private[logging] val root: Logger = get("")

  // ----- convenience methods:

  /** OFF is used to turn off logging entirely. */
  def OFF = Level.OFF

  /** Describes an event which will cause the application to exit immediately, in failure. */
  def FATAL = Level.FATAL

  /** Describes an event which will cause the application to fail to work correctly, but
   *  keep attempt to continue. The application may be unusable.
   */
  def CRITICAL = Level.CRITICAL

  /** Describes a user-visible error that may be transient or not affect other users. */
  def ERROR = Level.ERROR

  /** Describes a problem which is probably not user-visible but is notable and/or may be
   *  an early indication of a future error.
   */
  def WARNING = Level.WARNING

  /** Describes information about the normal, functioning state of the application. */
  def INFO = Level.INFO

  /** Describes information useful for general debugging, but probably not normal,
   *  day-to-day use.
   */
  def DEBUG = Level.DEBUG

  /** Describes information useful for intense debugging. */
  def TRACE = Level.TRACE

  /** ALL is used to log everything. */
  def ALL = Level.ALL

  /**
   * Return a map of log level values to the corresponding Level objects.
   */
  def levels: Map[Int, Level] = levelsMap

  /**
   * Return a map of log level names to the corresponding Level objects.
   */
  def levelNames: Map[String, Level] = levelNamesMap

  /**
   * Reset logging to an initial state, where all logging is set at
   * INFO level and goes to the console (stderr). Any existing log
   * handlers are removed.
   */
  def reset() = {
    clearHandlers()
    root.addHandler(new ConsoleHandler(new Formatter(), None))
  }

  /**
   * Remove all existing log handlers from all existing loggers.
   */
  def clearHandlers() = {
    foreach { logger =>
      logger.clearHandlers()
      logger.setLevel(null)
    }
  }

  /**
   * Return a logger for the given package name. If one doesn't already
   * exist, a new logger will be created and returned.
   */
  def get(name: String): Logger = {
    loggersCache.get(name) match {
      case logger: Logger =>
        logger
      case null =>
        val logger = new Logger(name, javalog.Logger.getLogger(name))
        logger.clearHandlers()
        logger.setLevel(null)
        logger.setUseParentHandlers(true)

        val oldLogger = loggersCache.putIfAbsent(name, logger)
        if (oldLogger != null) {
          oldLogger
        } else {
          logger
        }
    }
  }

  /** An alias for `get(name)` */
  def apply(name: String) = get(name)

  private def get(depth: Int): Logger = getForClassName(new Throwable().getStackTrace()(depth).getClassName)

  /**
   * Return a logger for the class name of the class/object that called
   * this method. Normally you would use this in a "private val"
   * declaration on the class/object. The class name is determined
   * by sniffing around on the stack.
   */
  def get(): Logger = get(2)

  /** An alias for `get()` */
  def apply() = get(2)

  private def getForClassName(className: String) = {
    if (className.endsWith("$")) {
      get(className.substring(0, className.length - 1))
    } else {
      get(className)
    }
  }

  /**
   * Return a logger for the package of the class given.
   */
  def get(cls: Class[_]): Logger = getForClassName(cls.getName)

  /** An alias for `get(class)` */
  def apply(cls: Class[_]) = get(cls)

  /**
   * Iterate the Logger objects that have been created.
   */
  def iterator: Iterator[Logger] = JavaConversions.asScalaIterator(loggersCache.values.iterator)

  def configure(config: List[LoggerConfig]) {
    clearHandlers()
    config.foreach { _() }
  }
}
