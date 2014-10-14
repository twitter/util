package com.twitter.cache

import com.twitter.util._
import java.util.concurrent.atomic.AtomicReference

/**
 * A single-value asynchronous cache with TTL.  The provider supplies the future value when invoked
 * and this class ensures that the provider is not invoked more frequently than the TTL.  If the
 * future fails, that failure is not cached. If more than one value is required, a FutureCache
 * backed by a guava cache with TTL may be more appropriate.
 *
 * This is useful in situations where a call to an external service returns a value that changes
 * infrequently and we need to access that value often, for example asking a service for a list of
 * features that it supports.
 *
 * A non-memoized function like this:
 *   def getData(): Future[T] = { ... }
 *
 * can be memoized with a TTL of 1 hour as follows:
 *   import com.twitter.util.TimeConversions._
 *   import com.twitter.cache.Refresh
 *   val getData: () => Future[T] = Refresh.every(1.hour) { ... }
 */
object Refresh {

  private val empty = (Future.never, Time.Bottom)

  /**
   * Create a memoized future with TTL.
   *
   * From Scala:
   *   Refresh.every(1.hour) { ... }
   *
   * From Java:
   *   Refresh.every(Duration.fromSeconds(3600), new Function0<Future<T>>() {
   *     @Override
   *     public Future<T> apply() {
   *       ...
   *     }
   *   });
   *
   * @param ttl The amount of time between refreshes
   * @param provider A provider function that returns a future to refresh
   * @return A memoized version of `operation` that will refresh on invocation
   *         if more than `ttl` time has passed since `operation` was last called.
   */
  def every[T](ttl: Duration)(provider: => Future[T]): () => Future[T] = {
    val ref = new AtomicReference[(Future[T], Time)](empty)
    def result(): Future[T] = ref.get match {
      case tuple@(cachedValue, lastRetrieved) =>
        val now = Time.now
        // interruptible allows the promise to be interrupted safely
        if (now < lastRetrieved + ttl) cachedValue.interruptible()
        else {
          val p = Promise[T]()
          val nextTuple = (p, now)
          if (ref.compareAndSet(tuple, nextTuple)) {
            val nextResult = provider onFailure { case NonFatal(e) =>
              // evict failed result lazily, next request will kick off a new request
              ref.compareAndSet(nextTuple, empty) // OK if we lose so no need to examine result
            }
            nextResult.proxyTo(p)
            p.interruptible() // interruptible allows the promise to be interrupted safely
          }
          else result()
        }
    }
    // Return result, which is a no-arg function that returns a future
    result
  }
}