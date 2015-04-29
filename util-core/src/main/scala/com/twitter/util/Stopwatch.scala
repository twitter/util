package com.twitter.util

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
 */
object Stopwatch extends Stopwatch {

  private[this] val systemNanoFn = () => System.nanoTime()

  def start(): Elapsed = Time.localGetTime() match {
    case Some(local) =>
      val startAt: Time = local()
      () => local() - startAt
    case None =>
      val startAt: Long = systemNanoFn()
      () => Duration.fromNanoseconds(systemNanoFn() - startAt)
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
 * A trivial implementation of [[Stopwatch]] for use as a null
 * object.
 */
object NilStopwatch extends Stopwatch {
  private[this] val fn = () => Duration.Bottom
  def start(): Elapsed = fn
}
