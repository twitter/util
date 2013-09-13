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
  def defaultLogLevel: Level = Level.INFO
  protected[this] val levelFlag = flag("log.level", defaultLogLevel, "Log level")
  protected[this] val outputFlag = flag("log.output", "/dev/stderr", "Output file")

  def loggerFactories: List[LoggerFactory] = LoggerFactory(
    node = "",
    level = Some(levelFlag()),
    handlers = FileHandler(outputFlag()) :: Nil
  ) :: Nil

  premain {
    Logger.configure(loggerFactories)
  }
}
