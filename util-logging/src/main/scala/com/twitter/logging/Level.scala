package com.twitter.logging

import java.util.{logging => javalog}

// replace java's ridiculous log levels with the standard ones.
sealed abstract class Level(val name: String, val value: Int) extends javalog.Level(name, value) {
  // for java compat
  def get(): Level = this
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

  private[logging] val AllLevels: Seq[Level] =
    Seq(OFF, FATAL, CRITICAL, ERROR, WARNING, INFO, DEBUG, TRACE, ALL)

  /**
   * Associate `java.util.logging.Level` and `Level` by their integer
   * values. If there is no match, we return `None`.
   */
  def fromJava(level: javalog.Level): Option[Level] =
    AllLevels.find(_.value == level.intValue)

  /**
   * Get a `Level` by its name, or `None` if there is no match.
   */
  def parse(name: String): Option[Level] = AllLevels.find(_.name == name)
}
