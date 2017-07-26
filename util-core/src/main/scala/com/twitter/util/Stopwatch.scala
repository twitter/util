package com.twitter.util

import java.util.concurrent.TimeUnit

/**
 * A stopwatch may be used to measure elapsed time.
 */
trait Stopwatch {
  type Elapsed = () => Duration

  /**
   * Start the stopwatch. The returned timer may be read any time,
   * returning the duration of time elapsed since start.
   */
  def start(): Elapsed
}

/**
 * The system [[Stopwatch]] measures elapsed time using `System.nanoTime`.
 *
 * Note that it works well with unit tests by respecting
 * time manipulation on [[Time]].
 *
 * @see [[Stopwatches]] for Java APIs.
 */
object Stopwatch extends Stopwatch {

  /**
   * A function that returns a Long that can be used for measuring elapsed time
   * in nanoseconds, using the system clock.
   *
   * Note that invoking this doesn't entail any allocations.
   */
  val systemNanos: () => Long =
    // we use nanos instead of current time millis because it increases monotonically
    () => System.nanoTime()

  /**
   * A function that returns a Long that can be used for measuring elapsed time
   * in nanoseconds, that is `Time` manipulation compatible.
   *
   * Useful for testing, but should not be used in production, since it uses a
   * non-monotonic time under the hood, and entails a few allocations.  For
   * production, see `systemNanos`.
   */
  val timeNanos: () => Long = () => Time.now.inNanoseconds

  /**
   * A function that returns a Long that can be used for measuring elapsed time
   * in microseconds, using the system clock.
   *
   * Note that invoking this doesn't entail any allocations.
   */
  val systemMicros: () => Long =
    // we use nanos instead of current time millis because it increases monotonically
    () => TimeUnit.MICROSECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS)

  /**
   * A function that returns a Long that can be used for measuring elapsed time
   * in microseconds, that is `Time` manipulation compatible.
   *
   * Useful for testing, but should not be used in production, since it uses a
   * non-monotonic time under the hood, and entails a few allocations.  For
   * production, see `systemMicros`.
   */
  val timeMicros: () => Long = () => Time.now.inMicroseconds

  /**
   * A function that returns a Long that can be used for measuring elapsed time
   * in milliseconds, using the system clock.
   *
   * Note that invoking this doesn't entail any allocations.
   */
  val systemMillis: () => Long =
    // we use nanos instead of current time millis because it increases monotonically
    () => TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS)

  /**
   * A function that returns a Long that can be used for measuring elapsed time
   * in milliseconds, that is `Time` manipulation compatible.
   *
   * Useful for testing, but should not be used in production, since it uses a
   * non-monotonic time under the hood, and entails a few allocations.  For
   * production, see `systemMillis`.
   */
  val timeMillis: () => Long = () => Time.now.inMilliseconds

  def start(): Elapsed = Time.localGetTime() match {
    case Some(local) =>
      val startAt: Time = local()
      () =>
        local() - startAt
    case None =>
      val startAt: Long = systemNanos()
      () =>
        Duration.fromNanoseconds(systemNanos() - startAt)
  }

  /**
   * A [[Stopwatch]] that always returns `dur` for the
   * elapsed [[Duration duration]].
   */
  def const(dur: Duration): Stopwatch = new Stopwatch {
    private[this] val fn = () => dur
    def start(): Elapsed = fn
  }
}

/**
 * Java APIs for [[Stopwatch]].
 */
object Stopwatches {
  import Stopwatch.Elapsed

  /** @see [[Stopwatch.start()]] */
  def start(): Elapsed =
    Stopwatch.start()

  /** @see [[Stopwatch.const]] */
  def constant(duration: Duration): Stopwatch =
    Stopwatch.const(duration)

  /** @see [[Stopwatch.systemNanos]] */
  def systemNanos: () => Long =
    Stopwatch.systemNanos

  /** @see [[Stopwatch.timeNanos]] */
  def timeNanos: () => Long =
    Stopwatch.timeNanos

  /** @see [[Stopwatch.systemMicros]] */
  def systemMicros: () => Long =
    Stopwatch.systemMicros

  /** @see [[Stopwatch.timeMicros]] */
  def timeMicros: () => Long =
    Stopwatch.timeMicros

  /** @see [[Stopwatch.systemMillis]] */
  def systemMillis: () => Long =
    Stopwatch.systemMillis

  /** @see [[Stopwatch.timeMillis]] */
  def timeMillis: () => Long =
    Stopwatch.timeMillis
}

/**
 * A trivial implementation of [[Stopwatch]] for use as a null
 * object.
 *
 * All calls to [[Stopwatch.start()]] return an [[Stopwatch.Elapsed]]
 * instance that always returns [[Duration.Bottom]] for its elapsed time.
 *
 * @see `NilStopwatch.get` for accessing this instance from Java.
 */
object NilStopwatch extends Stopwatch {
  private[this] val fn = () => Duration.Bottom

  def start(): Elapsed = fn

  /** Java API for getting an instance of this [[Stopwatch]]. */
  def get: Stopwatch = this
}
