package com.twitter.util

/**
 * A sleeper can relinquish control flow for a given duration.
 */
trait Sleeper {
  def sleep(duration: Duration)
}

/**
 * Uses the [[com.twitter.util.Thread.sleep]] to sleep.  Has millisecondly
 * precision.
 */
object ThreadSleeper extends Sleeper {
  def sleep(duration: Duration) {
    Thread.sleep(duration.inMilliseconds)
  }
}

/**
 * Uses the given [[com.twitter.util.Timer]] to sleep, by scheduling a time to
 * wake up and blocking until then.  Useful for testing in conjection with
 * [[com.twitter.util.MockTimer]].
 */
class TimerSleeper(timer: Timer) extends Sleeper {
  def sleep(duration: Duration) {
    Await.result(Future.sleep(duration)(timer))
  }
}
