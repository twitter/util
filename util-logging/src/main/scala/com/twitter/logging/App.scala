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
  protected[this] val levelFlag = flag("log.level", defaultLogLevel, "Log level")
  protected[this] val outputFlag = flag("log.output", defaultOutput, "Output file")

  def loggerFactories: List[LoggerFactory] = {
    val output = outputFlag()
    val level = Some(levelFlag())
    val handler =
      if (output == "/dev/stderr")
        ConsoleHandler(level = level)
      else
        FileHandler(output, level = level)

    LoggerFactory(
      node = "",
      level = level,
      handlers = handler :: Nil
    ) :: Nil
  }

  premain {
    Logger.configure(loggerFactories)
  }
}
