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
 * to configure the (default) logger setup.
 */
trait Logging { self: App =>
  import Logging._

  lazy val log = Logger(name)
  def defaultLogLevel: Level = Level.INFO
  private val levelFlag = flag("log.level", defaultLogLevel, "Log level")
  private val outputFlag = flag("log.output", "/dev/stderr", "Output file")

  premain {
    val factory = LoggerFactory(
      node = "",
      level = Some(levelFlag()),
      handlers = FileHandler(outputFlag()) :: Nil
    )
    Logger.configure(factory :: Nil)
  }
}
