package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.util._
import java.util.concurrent.{
  ArrayBlockingQueue,
  BlockingQueue,
  CancellationException,
  LinkedBlockingQueue,
  RejectedExecutionException
}

// implicitly a rate of 1 token / `interval`
private[concurrent] class Period(val interval: Duration) extends AnyVal {
  import AsyncMeter._

  def numPeriods(dur: Duration): Double =
    dur.inNanoseconds.toDouble / interval.inNanoseconds.toDouble

  def realInterval: Duration = interval.max(MinimumInterval)
}

private[concurrent] object Period {
  def fromBurstiness(size: Int, interval: Duration): Period = new Period(interval / size)
}

object AsyncMeter {
  private[concurrent] val MinimumInterval = 1.millisecond

  /**
   * Creates an [[AsyncMeter]] that allows smoothed out `permits` per
   * second, and has a maximum burst size of `permits` over one second.
   *
   * This is equivalent to `AsyncMeter.newMeter(permits, 1.second, maxWaiters)`.
   */
  def perSecond(permits: Int, maxWaiters: Int)(implicit timer: Timer): AsyncMeter =
    newMeter(permits, 1.second, maxWaiters)

  /**
   * Creates an [[AsyncMeter]] that allows smoothed out `permits` per second,
   * and has a maximum burst size of 1 permit over `1.second  / permits`.
   *
   * This method produces [[AsyncMeter]]s that might be placed before an
   * external API forcing a rate limit over a one second. For example, the
   * following meter rate limits its callers to make sure no more than 8 QPS
   * is sent at any point of time.
   *
   * {{{
   *  val meter = AsyncMeter.perSecondLimited(8, 100)
   * }}}
   *
   * This is equivalent to `AsyncMeter.newMeter(1, 1.second / permits, maxWaiters)`.
   *
   * @note If you don't need an exact limit, you'll be able to handle bursts\
   *       faster by using either [[newMeter]] or [[perSecond]].
   *
   * @note It's possible to get `permits` + 1 waiters to continue over the very first
   *       second, but the burst should be smoothed out after that.
   */
  def perSecondLimited(permits: Int, maxWaiters: Int)(implicit timer: Timer): AsyncMeter =
    newMeter(1, 1.second / permits, maxWaiters)

  /**
   * Creates an [[AsyncMeter]] that has a maximum burst size of `burstSize` over
   * `burstDuration`, and no more than `maxWaiters` waiters.  The `burstSize`
   * permits will be disbursed on a regular schedule, so that they aren't
   * bunched up.
   *
   * @param burstSize: the maximum number of waiters who may be allowed to
   * continue over `burstDuration`
   *
   * @param burstDuration: the duration over which we limit ourselves
   *
   * @param maxWaiters: the number of allowable waiters at a given time
   */
  def newMeter(
    burstSize: Int,
    burstDuration: Duration,
    maxWaiters: Int
  )(
    implicit timer: Timer
  ): AsyncMeter = {
    require(maxWaiters > 0, s"max waiters of $maxWaiters, which is <= 0 doesn't make sense")
    val q = new ArrayBlockingQueue[(Promise[Unit], Int)](maxWaiters)
    new AsyncMeter(burstSize, burstDuration, q)
  }

  /**
   * Creates an [[AsyncMeter]] that has a maximum burst size of `burstSize` over
   * `burstDuration`, and an unbounded number of waiters.  The `burstSize`
   * permits will be disbursed on a regular schedule, so that they aren't
   * bunched up.
   *
   * WARNING: Only use an unbounded number of waiters when some other
   * aspect of your implementation already bounds the number of
   * waiters. If there is no other bound, the waiters can use up your
   * process' resources.
   *
   * @param burstSize: the maximum number of waiters who may be allowed to
   * continue over `burstDuration`
   *
   * @param burstDuration: the duration over which we limit ourselves
   */
  def newUnboundedMeter(
    burstSize: Int,
    burstDuration: Duration
  )(
    implicit timer: Timer
  ): AsyncMeter =
    new AsyncMeter(burstSize, burstDuration, new LinkedBlockingQueue)

  /**
   * Allows the user to `await` on requests that have a wider width than the
   * `burstSize` specified in [[AsyncMeter]].
   *
   * WARNING: this means that you are able to arbitrarily exceed your
   * `burstSize` setting, so it violates the contract that you never exceed
   * `burstSize` within a given `burstDuration`.  Also, because of the
   * implementation, it consumes more than one slot from `maxWaiters`.
   */
  def extraWideAwait(permits: Int, meter: AsyncMeter): Future[Unit] = {
    if (permits > meter.burstSize) {
      val last = permits % meter.burstSize
      val num = permits / meter.burstSize
      val seqWithoutLast: Seq[Future[Unit]] = (0 until num).map(_ => meter.await(meter.burstSize))
      val seq = if (last == 0) seqWithoutLast else seqWithoutLast :+ meter.await(last)
      val result = Future.join(seq)
      result.onFailure { exc =>
        seq.foreach { f: Future[Unit] =>
          f.raise(exc)
        }
      }
      result
    } else meter.await(permits)
  }
}

/**
 * An asynchronous meter.
 *
 * Processes can create an asynchronously awaiting future, a "waiter" to wait
 * until the meter allows it to continue, which is when the meter can give it as
 * many permits as it asked for.  Up to `burstSize` permits are issued every
 * `burstDuration`.  If `maxWaiters` waiters are enqueued simultaneously, it
 * will reject further attempts to wait, until some of the tasks have been
 * executed.
 *
 * It may be appropriate to use this to smooth out bursty traffic, or if using a
 * resource that's rate-limited based on time.  However, to avoid overwhelming a
 * constrained resource that doesn't exert coordination controls like
 * backpressure, it's safer to limit based on [[AsyncSemaphore]], since it can
 * speed up if that resource speeds up, and slow down if that resource slows
 * down.
 *
 * {{{
 * // create a meter that allows 1 operation per second, and a max of 1000 waiting
 * val meter = new AsyncMeter(1, 1.second, 1000)
 *
 * def notMoreThanOncePerSecond[A](f: => Future[A]): Future[A] = {
 *   meter.await(1).handle { case t: RejectedExecutionException =>
 *     // do something else when too many waiters
 *   }.before {
 *     f
 *   }
 * }
 * }}}
 */
class AsyncMeter private(
    private[concurrent] val burstSize: Int,
    burstDuration: Duration,
    q: BlockingQueue[(Promise[Unit], Int)])(implicit timer: Timer) {

  require(burstSize > 0, s"burst size of $burstSize, which is <= 0 doesn't make sense")
  require(burstDuration > Duration.Zero,
    s"burst duration of $burstDuration, which is <= 0 nanoseconds doesn't make sense")

  private[this] val period = Period.fromBurstiness(burstSize, burstDuration)

  // if it's less frequent than 1 / millisecond, we release 1 every interval to make it hit that rate.
  // otherwise, we release N every millisecond
  private[this] val interval = period.realInterval
  private[this] val bucket: TokenBucket = TokenBucket.newBoundedBucket(burstSize)
  bucket.put(burstSize)

  // these are synchronized on this
  private[this] var remainder: Double = 0
  @volatile private[this] var running = false
  private[this] var task: Closable = Closable.nop
  private[this] var elapsed = Stopwatch.start()

  // TODO: we may want to check the Deadline and not bother scheduling it if its
  // position in line exceeds its Deadline.  However, if earlier nodes get
  // interrupted, it may jump its spot in line, so it may not be correct to
  // declare it dead in the water already.

  /**
   * Provides a [[com.twitter.util.Future]] that waits to be issued `permits`
   * permits until after the previously scheduled waiters have had their permits
   * issued.  Permits are spaced out evenly, so that they aren't issued in big
   * batches all at once.
   *
   * If a waiter is scheduled, but the existing queue is empty, it is delayed
   * until sufficient permits have built up.  If enough time has passed since
   * the last waiter was permitted so that permits would have built up while it
   * was waiting, it will be permitted immediately.
   *
   * If the returned [[com.twitter.util.Future]] is interrupted, we
   * try to cancel it. If it's successfully cancelled, the
   * [[com.twitter.util.Future]] is satisfied with a
   * [[java.util.concurrent.CancellationException]], and the permits
   * will not be issued, so a subsequent waiter can take advantage
   * of the permits.
   *
   * If `await` is invoked when there are already `maxWaiters` waiters waiting
   * for permits, the [[com.twitter.util.Future]] is immediately satisfied with
   * a [[java.util.concurrent.RejectedExecutionException]].
   *
   * If more permits are requested than `burstSize` then it returns a failed
   * [[java.lang.IllegalArgumentException]] [[com.twitter.util.Future]]
   * immediately.
   */
  def await(permits: Int): Future[Unit] = {
    if (permits > burstSize)
      return Future.exception(new IllegalArgumentException(
        s"Tried to await on $permits permits, but the maximum burst size was $burstSize"))

    // don't jump the queue-this is racy, but the race here is indistinguishable
    // from the synchronized behavior
    if (!running && updateAndGet(permits))
      return Future.Done

    // if the promise is enqueued, it is satisfied by the thread that removes it
    // from the queue.  if it's not enqueued, it is satisfied immediately.  this
    // guarantees that satisfying the thread is not racy--we also use
    // Promise#setValue or Promise#setException to ensure that if there's a
    // race, it will fail loudly.
    val p = Promise[Unit]
    val tup = (p, permits)

    if (q.offer(tup)) {
      p.setInterruptHandler { case t: Throwable =>
        // we synchronize removals, because we only want to satisfy when
        // the tokenbucket has enough space to remove, but we can't know
        // whether it has enough space or not without peeking.  after we
        // peek, and successfully remove from the tokenbucket, if the
        // promise is interrupted then there's a race between removing
        // and polling-by synchronizing on peek/poll and remove, it's
        // impossible to race.
        val rem = synchronized { q.remove(tup) }
        if (rem) {
          val e = new CancellationException("Request for permits was cancelled.")
          e.initCause(t)
          p.setException(e)
        }
      }
      restartTimerIfDead()
      p
    } else {
      Future.exception(new RejectedExecutionException(
        "Tried to wait when there were already the maximum number of waiters."))
    }
  }

  /**
   * Returns the current number of outstanding waiters in the queue
   */
  def numWaiters(): Int = q.size()

  private[this] def updateAndGet(tokens: Int): Boolean = {
    bucket.put(getNumRefreshTokens())
    bucket.tryGet(tokens)
  }

  // we refresh the bucket with as many tokens as we have accrued since we last
  // refreshed.
  private[this] def getNumRefreshTokens(): Int = synchronized {
    val newTokens = period.numPeriods(elapsed())
    elapsed = Stopwatch.start()
    val num = newTokens + remainder
    val floor = math.floor(num)
    remainder = num - floor
    floor.toInt
  }

  private[this] def restartTimerIfDead(): Unit = synchronized {
    if (!running) {
      running = true
      task = timer.schedule(interval) {
        allow()
      }
    }
  }

  // it's safe to race on allow, because polling loop is locked
  private[this] final def allow(): Unit = {
    // tokens represents overflow from lack of granularity.  we don't want to
    // store more than `burstSize` tokens, but we want to be able to process
    // load at the rate we advertise to, even if we can't refresh to `burstSize`
    // as fast as `burstDuration` would like.  we get around this by ensuring
    // that we disburse the full amount to waiters, which ensures correct
    // behavior for small `burstSize` and `burstDuration` below the minimum
    // granularity.
    var tokens = getNumRefreshTokens()

    if (tokens > burstSize) {
      tokens -= burstSize
      bucket.put(burstSize)
    } else {
      bucket.put(tokens)
      tokens = 0
    }

    // we loop here so that we can satisfy more than one promise at a time.
    // imagine that we start with no tokens, we distribute ten tokens, and our
    // waiters are waiting for 4, 1, 6, 3 tokens.  we should distribute 4, and
    // 1, and ask 6 and 3 to keep waiting until we have more tokens.
    while (true) {
      // we go through the `control` runaround to avoid triggering the
      // closures on the promise while we hold the lock.
      // TODO: investigate using an explicit lock so we can just call unlock()
      // instead of tying the lock to the scope.
      val control = this.synchronized {
        q.peek() match {
          case null =>
            running = false

            // it's safe to close here because refreshTokens will grab all of the
            // tokens that we're missing with the Stopwatch.
            task.close()
            None
          case (p, num) if num < tokens =>
            tokens -= num
            q.poll() // we wait to remove until after we're able to get tokens
            Some(p)
          case (p, num) if bucket.tryGet(num - tokens) =>
            // we must zero tokens because we're implicitly pulling from the
            // tokens first, and then the token bucket
            tokens = 0
            q.poll() // we wait to remove until after we're able to get tokens
            Some(p)
          case _ =>
            None
        }
      }
      control match {
        case Some(p) => p.setValue(())
        case None => return ()
      }
    }
  }
}
