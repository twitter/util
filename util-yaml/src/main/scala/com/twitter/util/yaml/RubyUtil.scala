package com.twitter.util.yaml

import com.twitter.util.Duration
import com.twitter.conversions.time._

/** Ruby/Rails utilities for Monorail compatibility. */
object RubyUtil {
  val ErbRegex          = "<%=([^%]*)%>".r

  // erb in format <%= 1.seconds * scale %>
  val ErbNum            = """\A(\d+(?:\.\d+)?)\z""".r
  val ErbNumTimeUnit    = """\A(\d+)\.(\w+?)s?\z""".r
  val ErbScaleTimeUnit  = """\A\((\d+)\.(\w+?)s? \* (\d+(?:\.\d+)?)\).to_i\z""".r

  // Alternatve erb format e.g. <%= T_10_SECONDS %>
  val ErbNumTimeUnitAlt = """\AT_(\d+)_(\w+?)S?\z""".r

  /** "Evaluate" a subset of ERB in the string.  This just handles numbers and dates.
    * (This is to support ERB used in Twitter configuration files.) */

  def evaluateErb(erb: String): String =
    ErbRegex.replaceAllIn(erb, m => rubyToSeconds(m.group(1).trim))

  def erbToDuration(erb: String): Duration = erb match {
    case ErbRegex(ruby) => (rubyToSeconds(ruby.trim).toDouble * 1000000L).toLong.microseconds
    case _ => throw new IllegalArgumentException("Invalid erb syntax: " + erb)
  }

  def rubyToSeconds(ruby: String): String = ruby match {
    case ErbNum(seconds) => seconds
    case ErbNumTimeUnit(value, timeUnit) =>
      convertErb(value, timeUnit, "1")
    case ErbScaleTimeUnit(value, timeUnit, scale) =>
      convertErb(value, timeUnit, scale)
    case ErbNumTimeUnitAlt(value, timeUnit) =>
      convertErb(value, timeUnit.toLowerCase, "1")
    case _ =>
      throw new UnsupportedOperationException("Unknown ruby in ERB: " + ruby)
  }

  private def convertErb(valueStr: String, timeUnit: String, scaleStr: String): String = {
    val value = valueStr.toLong
    val scale = scaleStr.toDouble
    val rv = timeUnit match {
      case "second" =>
        value * scale.toDouble
      case "minute" =>
        value * 60 * scale.toDouble
      case "hour" =>
        value * 60 * 60 * scale.toDouble
      case "day" =>
        value * 60 * 60 * 24 * scale.toDouble
      case _ =>
        throw new UnsupportedOperationException("unknown time unit %s".format(timeUnit))
    }
    rv.toLong.toString
  }

  private val RubyIntRegex = """\A\s*(-?\d+).*\Z""".r
  /**
   * Convert s to an Integer as Ruby would: initial whitespace and zeros are
   * skipped, non-digits after the number are ignored, and the default is 0.
   */
  def toInt(s: String): Int = {
    RubyIntRegex.findFirstMatchIn(s) match {
      case Some(sMatch) =>
        try {
          sMatch.group(1).toInt
        } catch {
          case e: NumberFormatException => 0
        }
      case None =>
        0
    }
  }

  /** Convert s to a Long as Ruby would: initial whitespace and zeros are
    * skipped, non-digits after the number are ignored, and the default is 0L. */
  def toLong(s: String): Long = {
    RubyIntRegex.findFirstMatchIn(s) match {
      case Some(sMatch) =>
        try {
          sMatch.group(1).toLong
        } catch {
          case e: NumberFormatException => 0L
        }
      case None =>
        0L
    }
  }
}
