package com.twitter.util

import java.io.Serializable
import java.util.concurrent.TimeUnit

object Duration extends TimeLikeOps[Duration] {

  def fromNanoseconds(nanoseconds: Long): Duration = new Duration(nanoseconds)

  // This is needed for Java compatibility.
  override def fromFractionalSeconds(seconds: Double): Duration = super.fromFractionalSeconds(seconds)
  override def fromSeconds(seconds: Int): Duration = super.fromSeconds(seconds)
  override def fromMilliseconds(millis: Long): Duration = super.fromMilliseconds(millis)
  override def fromMicroseconds(micros: Long): Duration = super.fromMicroseconds(micros)

  val NanosPerMicrosecond = 1000L
  val NanosPerMillisecond = NanosPerMicrosecond * 1000L
  val NanosPerSecond = NanosPerMillisecond * 1000L
  val NanosPerMinute = NanosPerSecond * 60
  val NanosPerHour = NanosPerMinute * 60
  val NanosPerDay = NanosPerHour * 24

  /**
   * Create a duration from a [[java.util.concurrent.TimeUnit]].
   * Synonym for `apply`.
   */
  def fromTimeUnit(value: Long, unit: TimeUnit) = apply(value, unit)

  /**
   * Create a duration from a [[java.util.concurrent.TimeUnit]].
   */
  def apply(value: Long, unit: TimeUnit): Duration = {
    val ns = TimeUnit.NANOSECONDS.convert(value, unit)
    fromNanoseconds(ns)
  }

  @deprecated("use time.untilNow", "2011-05-03") // date is a guess
  def since(time: Time) = Time.now.since(time)

  /**
   * Duration `Top` is greater than any other duration, except for
   * itself. `Top`'s complement is `Bottom`.
   */
  val Top: Duration = new Duration(Long.MaxValue) {
    override def hashCode = System.identityHashCode(this)

    /** Top is equal only to Top and greater than every finite duration */
    override def compare(that: Duration) =
      if (that eq Undefined) -1
      else if (that eq Top) 0
      else 1

    override def *(x: Long): Duration =
      if (x == 0) Undefined
      else if (x < 0) Bottom
      else Top

    override def *(x: Double): Duration =
      if (x == 0.0) Undefined
      else if (x < 0.0) Bottom
      else Top

    override def /(x: Long): Duration =
      if (x == 0) Undefined
      else if (x < 0) Bottom
      else Top

    override def /(x: Double): Duration =
      if (x == 0.0) Undefined
      else if (x < 0.0) Bottom
      else Top

    override def isFinite = false

    override def %(x: Duration) = Undefined
    override def abs = this
    override def fromNow = Time.Top
    override def ago = Time.Bottom
    override def afterEpoch = Time.Top
    override def +(delta: Duration) = delta match {
      case Bottom | Undefined => Undefined
      case _ => this
    }
    override def unary_- = Bottom
    override def toString = "Duration.Top"

    private def writeReplace(): Object = DurationBox.Top()
  }

  /**
   * Duration `Bottom` is smaller than any other duration, except for
   * itself. `Bottom`'s complement is `Top`.
   */
  val Bottom: Duration = new Duration(Long.MinValue) {
    override def hashCode = System.identityHashCode(this)

    /** Bottom is equal to Bottom, but smaller than everything else */
    override def compare(that: Duration) = if (this eq that) 0 else -1

    /** Scaling arithmetic is Bottom preserving. */
    override def *(x: Long): Duration =
      if (x == 0) Undefined
      else if (x < 0) Top
      else Bottom

    override def *(x: Double): Duration =
      if (x == 0.0) Undefined
      else if (x < 0.0) Top
      else Bottom

    override def /(x: Long): Duration =
      if (x == 0) Undefined
      else if (x < 0) Top
      else Bottom

    override def /(x: Double): Duration =
      if (x == 0.0) Undefined
      else if (x < 0.0) Top
      else Bottom

    override def %(x: Duration): Duration = Undefined

    override def abs = Top
    override def fromNow = Time.Bottom
    override def ago = Time.Top
    override def afterEpoch = Time.Bottom

    override def isFinite = false

    override def +(delta: Duration) = delta match {
      case Top | Undefined => Undefined
      case _ => this
    }

    override def unary_- = Top
    override def toString = "Duration.Bottom"

    private def writeReplace(): Object = DurationBox.Bottom()
  }

  val Undefined: Duration = new Duration(0) {
    override def hashCode = System.identityHashCode(this)

    override def compare(that: Duration) = if (this eq that) 0 else 1

    override def *(x: Long): Duration = this
    override def *(x: Double): Duration = this
    override def /(x: Long): Duration = this
    override def /(x: Double): Duration = this
    override def %(x: Duration): Duration = this
    override def abs = this
    override def fromNow = Time.Undefined
    override def ago = Time.Undefined
    override def afterEpoch = Time.Undefined
    override def +(delta: Duration) = this
    override def unary_- = this
    override def isFinite = false

    override def toString = "Duration.Undefined"

    private def writeReplace(): Object = DurationBox.Undefined()
  }

  @deprecated("use Duration.Zero", "5.4.0")
  val zero: Duration = Zero
  /** Synonym to `Top` */
  @deprecated("use Duration.Top", "5.4.0")
  val forever: Duration = Top
  /** Synonym to `Top` */
  @deprecated("use Duration.Top", "5.4.0")
  val eternity: Duration = Top
  @deprecated("Use Duration.Top", "5.4.0")
  val MaxValue: Duration = Top
  @deprecated("Use Duration.Zero", "5.4.0")
  val MinValue: Duration = Zero

  /**
   * Returns how long it took, in millisecond granularity, to run the function f.
   */
  @deprecated("use Stopwatch instead", "5.4.0")
  def inMilliseconds[T](f: => T): (T, Duration) = {
    val start = Time.now
    val rv = f
    val duration = Time.now - start
    (rv, duration)
  }

  /**
   * Returns how long it took, in nanosecond granularity, to run the function f.
   */
  @deprecated("Use Stopwatch", "5.4.0")
  def inNanoseconds[T](f: => T): (T, Duration) = {
    val start = System.nanoTime
    val rv = f
    val duration = fromNanoseconds(System.nanoTime - start)
    (rv, duration)
  }

  private val timeUnits = Seq(
    TimeUnit.DAYS,
    TimeUnit.HOURS,
    TimeUnit.MINUTES,
    TimeUnit.SECONDS,
    TimeUnit.MILLISECONDS,
    TimeUnit.MICROSECONDS,
    TimeUnit.NANOSECONDS)

  private val nameToUnit: Map[String, TimeUnit] =
    TimeUnit.values().flatMap { u =>
      val pluralK = u.toString.toLowerCase
      val singularK = pluralK dropRight 1
      Seq(pluralK -> u, singularK -> u)
    }.toMap

  private val SingleDurationRegex =
    """\s*([+-]?)\s*(?:([0-9]+)\.([a-z]+)|duration\.(top|bottom|undefined))""".r

  private val FullDurationRegex = ("(" + SingleDurationRegex.pattern.pattern + """)+\s*""").r

  /**
   * Parse a String representation of a duration. This method will
   * parse any duration generated by Duration.toString.
   *
   * The format is either one of the special values, or non-empty
   * sequence of durations. Each duration is a sign, an integer, a
   * dot, and a unit. The unit may be plural or singular. The parser
   * will ignore whitespace around signs and at the beginning and end.
   * (That is, it accepts "1.second + 1.minute" and " 1.second ".)
   * It's permissible to omit the sign before the first duration.
   *
   * The special values are "Duration.Top", "Duration.Bottom" and
   * "Duration.Undefined".
   *
   * The parser is case-insensitive.
   *
   * @throws RuntimeException if the string cannot be parsed.
   */
  def parse(s: String) = {
    val ss = s.toLowerCase
    ss match {
      case FullDurationRegex(_*) =>
        SingleDurationRegex.findAllIn(ss).matchData.zipWithIndex map {
          case (m, i) =>
            val List(signStr, numStr, unitStr, special) = m.subgroups
            val absDuration = special match {
              case "top"       => Top
              case "bottom"    => Bottom
              case "undefined" => Undefined
              case _           =>
                val u = nameToUnit.get(unitStr) match {
                  case Some(t) => t
                  case None    => throw new NumberFormatException("Invalid unit: " + unitStr)
                }
                Duration(numStr.toLong, u)
            }

            signStr match {
              case "-"         => -absDuration

              // It's only OK to omit the sign for the first duration.
              case "" if i > 0 =>
                throw new NumberFormatException("Expected a sign between durations")

              case _           => absDuration
            }

        // It's OK to use reduce because the regex ensures that there is
        // at least one element
        } reduce { _ + _ }
      case _ => throw new NumberFormatException("Invalid duration: " + s)
    }
  }
}

private[util] object DurationBox {
  case class Finite(nanos: Long) extends Serializable {
    private def readResolve(): Object = Duration.fromNanoseconds(nanos)
  }

  case class Top() extends Serializable {
    private def readResolve(): Object = Duration.Top
  }

  case class Bottom() extends Serializable {
    private def readResolve(): Object = Duration.Bottom
  }

  case class Undefined() extends Serializable {
    private def readResolve(): Object = Duration.Undefined
  }
}

/**
 * A `Duration` represents the span between two points in time. It represents
 * this with a signed long, and thus the largest representable duration is:
 *
 *   106751.days+23.hours+47.minutes+16.seconds
 *     +854.milliseconds+775.microseconds+807.nanoseconds
 *
 * Durations may be constructed via its companion object,
 * `Duration.fromNanoseconds`, `Duration.fromSeconds`, etc. or by
 * using the time conversions:
 *
 * {{{
 * import com.twitter.conversions.time._
 *
 * 3.days+4.nanoseconds
 * }}}
 *
 * In addition to the timespans in the range of `Long.MinValue` to
 * `Long.MaxValue` nanoseconds, durations have two distinguished
 * values: `Duration.Top` and `Duration.Bottom`. These have special
 * semantics: `Top` is greater than every other duration, save for
 * itself; `Bottom` is smaller than any duration except for
 * itself — they act like positive and negative infinity, and
 * their arithmetic follows. This is useful for representing durations
 * that are truly infinite; for example the absence of a timeout.
 */
sealed class Duration private[util] (protected val nanos: Long) extends {
  protected val ops = Duration
} with TimeLike[Duration] with Serializable {
  import ops._

  def inNanoseconds = nanos

  /**
   * Returns the length of the duration in the given TimeUnit.
   *
   * In general, a simpler approach is to use the named methods (eg. inSeconds)
   * However, this is useful for more programmatic call sites.
   */
  def inUnit(unit: TimeUnit): Long =
    unit.convert(inNanoseconds, TimeUnit.NANOSECONDS)

  /**
   * toString produces a representation that
   *
   * - loses no information
   * - is easy to read
   * - can be read back in if com.twitter.conversions.time._ is imported
   *
   * An example:
   *
   * com.twitter.util.Duration(9999999, java.util.concurrent.TimeUnit.MICROSECONDS)
   * res0: com.twitter.util.Duration = 9.seconds+999.milliseconds+999.microseconds
   */
  override def toString: String = {
    if (nanos == 0)
      return "0.seconds"

    val s = new StringBuilder
    var ns = nanos
    for (u <- timeUnits) {
      val v = u.convert(ns, TimeUnit.NANOSECONDS)
      if (v != 0) {
        ns -= TimeUnit.NANOSECONDS.convert(v, u)
        if (v > 0 && !s.isEmpty)
          s.append("+")
        s.append(v.toString)
        s.append(".")
        s.append(u.name.toLowerCase)
      }
    }

    s.toString()
  }

  override def equals(other: Any) = other match {
    case d: Duration => (this compare d) == 0
    case _ => false
  }

  override def hashCode = nanos.hashCode

  /**
   * Scales this `Duration` by multiplying by `x`.
   */
  def *(x: Long) = try fromNanoseconds(LongOverflowArith.mul(nanos, x)) catch {
    case _: LongOverflowException if nanos < 0 == x < 0 => Top
    case _: LongOverflowException => Bottom
  }

  /**
   * Scales this `Duration` by multiplying by `x`.
   */
  def *(x: Double): Duration = (nanos * x) match {
    case product if java.lang.Double.isNaN(product) => Undefined
    case Double.PositiveInfinity => Top
    case Double.NegativeInfinity => Bottom
    case product =>
      val productLong = product.toLong
      if (productLong == Long.MaxValue) Top
      else if (productLong == Long.MinValue) Bottom
      else fromNanoseconds(productLong)
  }

  /**
   * Scales this `Duration` by dividing by `x`.
   */
  def /(x: Long): Duration =
    if (x != 0) fromNanoseconds(nanos / x)
    else if (nanos == 0) Undefined
    else if (nanos < 0) Bottom
    else Top

  /**
   * Scales this `Duration` by dividing by `x`.
   */
  def /(x: Double): Duration =
    if (x == 0.0) this / 0
    else this * (1.0/x)

  /**
   * Scales this `Duration` by modding by `x`.
   */
  def %(x: Duration): Duration = x match {
    case Undefined | Nanoseconds(0) => Undefined
    case Nanoseconds(ns) => fromNanoseconds(nanos % ns)
    case Top | Bottom => this
  }

  /**
   * Converts negative durations to positive durations.
   */
  def abs = if (nanos < 0) -this else this

  def fromNow = Time.now + this
  def ago = Time.now - this
  def afterEpoch = Time.epoch + this

  // Note that Long.MinValue receives special treatment here because
  // of two's complement: -Long.MinValue == Long.MinValue.
  def unary_-  =
    if (inNanoseconds == Long.MinValue) Top
    else ops.fromNanoseconds(-inNanoseconds)

  def diff(that: Duration) = this - that

  def isFinite = true

  private def writeReplace(): Object = DurationBox.Finite(inNanoseconds)

  /**
   * @see operator +
   */
  def plus(delta: Duration): Duration = this + delta

  /**
   * @see operator -
   */
  def minus(delta: Duration): Duration = this - delta

  /**
   * Negates this `Duration`.
   */
  def neg: Duration = -this

  /**
   * @see operator *
   */
  def mul(x: Long): Duration = this * x

  /**
   * @see operator *
   */
  def mul(x: Double): Duration = this * x

  /**
   * @see operator /
   */
  def div(x: Long): Duration = this / x

  /**
   * @see operator /
   */
  def div(x: Double): Duration = this / x

  /**
   * @see operator %
   */
  def rem(x: Duration): Duration = this % x

  // for Java-compatibility
  override def floor(increment: Duration): Duration = super.floor(increment)
  override def ceil(increment: Duration): Duration = super.ceil(increment)
}
