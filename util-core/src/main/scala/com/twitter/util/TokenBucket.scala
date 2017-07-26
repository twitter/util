package com.twitter.util

/**
 * A token bucket is used to control the relative rates of two
 * processes: one fills the bucket, another empties it.
 */
abstract class TokenBucket {

  /**
   * Put `n` tokens into the bucket.
   *
   * @param n: the number of tokens to remove from the bucket.
   * Must be >= 0.
   */
  def put(n: Int): Unit

  /**
   * Try to get `n` tokens out of the bucket.
   *
   * @param n: the number of tokens to remove from the bucket.
   * Must be >= 0.
   *
   * @return true if successful
   */
  def tryGet(n: Int): Boolean

  /**
   * The number of tokens currently in the bucket.
   */
  def count: Long
}

object TokenBucket {

  /**
   * A token bucket that doesn't exceed a given bound.
   *
   * This is threadsafe, and does not require further synchronization.
   * The token bucket starts empty.
   *
   * @param limit: the upper bound on the number of tokens in the bucket.
   */
  def newBoundedBucket(limit: Long): TokenBucket = new TokenBucket {
    private[this] var counter = 0L

    /**
     * Put `n` tokens into the bucket.
     *
     * If putting in `n` tokens would overflow `limit` tokens, instead sets the
     * number of tokens to be `limit`.
     */
    def put(n: Int): Unit = {
      require(n >= 0)
      synchronized {
        counter = math.min((counter + n), limit)
      }
    }

    def tryGet(n: Int): Boolean = {
      require(n >= 0)
      synchronized {
        val ok = counter >= n
        if (ok) {
          counter -= n
        }
        ok
      }
    }

    def count: Long = synchronized { counter }
  }

  /**
   * A leaky bucket expires tokens after approximately `ttl` time.
   * Thus, a bucket left alone will empty itself.
   *
   * @param ttl The (approximate) time after which a token will
   * expire.
   *
   * @param reserve The number of reserve tokens over the TTL
   * period. That is, every `ttl` has `reserve` tokens in addition to
   * the ones added to the bucket.

   * @param nowMs The current time in milliseconds
   */
  def newLeakyBucket(ttl: Duration, reserve: Int, nowMs: () => Long): TokenBucket =
    new TokenBucket {
      private[this] val w = WindowedAdder(ttl.inMilliseconds, 10, nowMs)

      def put(n: Int): Unit = {
        require(n >= 0)
        w.add(n)
      }

      def tryGet(n: Int): Boolean = {
        require(n >= 0)

        synchronized {
          // Note that this is a bit sloppy: the answer to w.sum
          // can change before we're able to decrement it. That's
          // ok, though, because the debit will simply roll over to
          // the next window.
          //
          // We could also add sloppiness here: any sum > 0 we
          // can debit, but the sum just rolls over.
          val ok = count >= n
          if (ok)
            w.add(-n)
          ok
        }
      }

      def count: Long = w.sum() + reserve
    }

  /**
   * A leaky bucket expires tokens after approximately `ttl` time.
   * Thus, a bucket left alone will empty itself.
   *
   * @param ttl The (approximate) time after which a token will
   * expire.
   *
   * @param reserve The number of reserve tokens over the TTL
   * period. That is, every `ttl` has `reserve` tokens in addition to
   * the ones added to the bucket.
   */
  def newLeakyBucket(ttl: Duration, reserve: Int): TokenBucket =
    newLeakyBucket(ttl, reserve, Stopwatch.systemMillis)
}
