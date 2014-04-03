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
 * The system [[com.twitter.util.Stopwatch]] measures elapsed time
 * using [[System.nanoTime]].
 */
object Stopwatch extends Stopwatch {

  def start(): Elapsed = {
    val timeFn = Time.localGetTime().getOrElse(() => Time.fromNanoseconds(System.nanoTime()))
    val off = timeFn()
      () => timeFn() - off
  }
  
  def const(dur: Duration): Stopwatch = new Stopwatch {
    private[this] val fn = () => dur
    def start() = fn
  }
}
