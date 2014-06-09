package com.twitter.logging

import com.twitter.app.{App, Flaggable}
import com.twitter.util.{Throw, Return}

object Logging {
  implicit object LevelFlaggable extends Flaggable[Level] {
    def parse(s: String) =
      if (Logger.levelNames contains s)
        Logger.levelNames(s)
      else
        throw new Exception("Invalid log level: "+s)
  }

  implicit object PolicyFlaggable extends Flaggable[Policy] {
    def parse(s: String) = Policy.parse(s)
  }
}

/**
 * A [[com.twitter.app.App]] mixin to use for logging. Defines flags
 * to configure the (default) logger setup.  When adding logging to a
 * twitter-server, mix in a trait that extends Logging but overrides factories.
 */
trait Logging { self: App =>
  import Logging._

  lazy val log = Logger(name)
  def defaultOutput: String = "/dev/stderr"
  def defaultLogLevel: Level = Level.INFO
  def defaultRollPolicy: Policy = Policy.Never
  def defaultAppend: Boolean = true
  def defaultRotateCount: Int = -1
  protected[this] val outputFlag = flag("log.output", defaultOutput, "Output file")
  protected[this] val levelFlag = flag("log.level", defaultLogLevel, "Log level")

  // FileHandler-related flags are ignored if outputFlag is not overridden.
  protected[this] val rollPolicyFlag = flag("log.rollPolicy", defaultRollPolicy,
    "When or how frequently to roll the logfile. " +
      "See com.twitter.logging.Policy#parse documentation for DSL details."
  )
  protected[this] val appendFlag =
    flag("log.append", defaultAppend, "If true, appends to existing logfile. Otherwise, file is truncated.")
  protected[this] val rotateCountFlag =
    flag("log.rotateCount", defaultRotateCount, "How many rotated logfiles to keep around")

  /**
   * By default, the root [[com.twitter.logging.LoggerFactory]] only has a single
   * [[com.twitter.logging.Handler]] which is configured via command line flags.
   * You can override this method to add additional handlers.
   */
  def handlers: List[() => Handler] = {
    val output = outputFlag()
    val level = Some(levelFlag())
    val handler =
      if (output == "/dev/stderr")
        ConsoleHandler(level = level)
      else
        FileHandler(
          output,
          rollPolicyFlag(),
          appendFlag(),
          rotateCountFlag(),
          level = level
        )
    handler :: Nil
  }

  def loggerFactories: List[LoggerFactory] = {
    LoggerFactory(
      node = "",
      level = Some(levelFlag()),
      handlers = handlers
    ) :: Nil
  }

  premain {
    Logger.configure(loggerFactories)
  }
}
