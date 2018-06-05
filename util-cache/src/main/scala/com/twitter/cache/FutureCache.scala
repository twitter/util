package com.twitter.cache

import com.twitter.util.Future
import java.util.concurrent.ConcurrentMap

/**
 * FutureCache is used to represent an in-memory, in-process, asynchronous cache.
 *
 * Every cache operation is atomic.
 *
 * Any correct implementation should make sure that you evict failed
 * results, and don't interrupt the underlying request that has been
 * fired off.  [[EvictingCache]] and [[Future#interrupting]] are
 * useful tools for building correct FutureCaches.  A reference
 * implementation for caching the results of an asynchronous function
 * can be found at [[FutureCache$.default]].
 */
abstract class FutureCache[K, V] {

  /**
   * Gets the cached Future.
   *
   * @return None if a value hasn't been specified for that key yet
   *         Some(ksync computation) if the value has been specified.  Just
   *         because this returns Some(..) doesn't mean that it has been
   *         satisfied, but if it hasn't been satisfied, it's probably
   *         in-flight.
   */
  def get(key: K): Option[Future[V]]

  /**
   * Gets the cached Future, or if it hasn't been returned yet, computes it and
   * returns that value.
   */
  def getOrElseUpdate(key: K)(compute: => Future[V]): Future[V]

  /**
   * Unconditionally sets a value for a given key
   */
  def set(key: K, value: Future[V]): Unit

  /**
   * Evicts the contents of a `key` if the old value is `value`.
   *
   * Since [[com.twitter.util.Future]] uses reference equality, you must use the
   * same object reference to evict a value.
   *
   * @return true if the key was evicted
   *         false if the key was not evicted
   */
  def evict(key: K, value: Future[V]): Boolean

  /**
   * @return the number of results that have been computed successfully or are in flight.
   */
  def size: Int
}

/**
 * A proxy for [[FutureCache]]s, useful for wrap-but-modify.
 */
abstract class FutureCacheProxy[K, V](underlying: FutureCache[K, V]) extends FutureCache[K, V] {
  def get(key: K): Option[Future[V]] = underlying.get(key)

  def getOrElseUpdate(key: K)(compute: => Future[V]): Future[V] =
    underlying.getOrElseUpdate(key)(compute)

  def set(key: K, value: Future[V]): Unit = underlying.set(key, value)

  def evict(key: K, value: Future[V]): Boolean = underlying.evict(key, value)

  def size: Int = underlying.size
}

/**
 * The FutureCache object provides the public interface for constructing
 * FutureCaches.  Once you've constructed a basic FutureCache, you should almost
 * always wrap it with default.  Normal usage looks like:
 *
 * val fn: K => Future[V]
 * val map = (new java.util.concurrent.ConcurrentHashMap[K, V]()).asScala
 * val cachedFn: K => Future[V] = FutureCache.default(fn, FutureCache.fromMap(map))
 *
 * We typically recommend that you use `Caffeine` Caches via
 * [[com.twitter.cache.caffeine.CaffeineCache]].
 */
object FutureCache {

  /**
   * A [[com.twitter.cache.FutureCache]] backed by a
   * [[java.util.concurrent.ConcurrentMap]].
   */
  def fromMap[K, V](fn: K => Future[V], map: ConcurrentMap[K, Future[V]]): K => Future[V] =
    default(fn, new ConcurrentMapCache(map))

  /**
   * Encodes keys, producing a Cache that takes keys of a different type.
   */
  def keyEncoded[K, V, U](encode: K => V, cache: FutureCache[V, U]): FutureCache[K, U] =
    new KeyEncodingCache(encode, cache)

  /**
   * Creates a function which caches the results of `fn` in an
   * [[com.twitter.cache.FutureCache]].  Ensures that failed Futures are evicted,
   * and that interrupting a Future returned to you by this function is safe.
   *
   * @see [[standard]] for the equivalent Java API.
   */
  def default[K, V](fn: K => Future[V], cache: FutureCache[K, V]): K => Future[V] =
    AsyncMemoize(fn, new EvictingCache(cache)) andThen { f: Future[V] =>
      f.interruptible()
    }

  /**
   * Alias for [[default]] which can be called from Java.
   */
  def standard[K, V](fn: K => Future[V], cache: FutureCache[K, V]): K => Future[V] =
    default(fn, cache)

}
