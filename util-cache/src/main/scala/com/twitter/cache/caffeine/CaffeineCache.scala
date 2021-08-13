package com.twitter.cache.caffeine

import com.github.benmanes.caffeine.cache.{Cache, LoadingCache}
import com.twitter.cache._
import com.twitter.util.Future
import java.util.function.Function

/**
 * A [[com.twitter.cache.FutureCache]] backed by a
 * `com.github.benmanes.caffeine.cache.Cache`.
 *
 * Any correct implementation should make sure that you evict failed results,
 * and don't interrupt the underlying request that has been fired off.
 * [[EvictingCache$]] and interrupting [[com.twitter.util.Future]]s are useful tools for building
 * correct FutureCaches.  A reference implementation for caching the results of
 * an asynchronous function with a caffeine Cache can be found at
 * [[CaffeineCache$.fromCache]].
 */
class CaffeineCache[K, V](cache: Cache[K, Future[V]])
    extends ConcurrentMapCache[K, V](cache.asMap) {
  override def getOrElseUpdate(k: K)(v: => Future[V]): Future[V] =
    cache.get(
      k,
      new Function[K, Future[V]] {
        def apply(k: K): Future[V] = v
      })
}

/**
 * A [[com.twitter.cache.FutureCache]] backed by a
 * `com.github.benmanes.caffeine.cache.LoadingCache`.

 * Any correct implementation should make sure that you evict failed results,
 * and don't interrupt the underlying request that has been fired off.
 * [[EvictingCache$]] and interrupting [[com.twitter.util.Future]]s are useful tools for building
 * correct FutureCaches.  A reference implementation for caching the results of
 * an asynchronous function with a caffeine LoadingCache can be found at
 * [[CaffeineCache$.fromLoadingCache]].
 */
class LoadingFutureCache[K, V](cache: LoadingCache[K, Future[V]])
    extends CaffeineCache[K, V](cache)
    with (K => Future[V]) {
  // the contract for LoadingCache is that it can't return null from get.
  def apply(key: K): Future[V] = cache.get(key)

  override def get(key: K): Option[Future[V]] = Some(apply(key))
}

object CaffeineCache {

  /**
   * Creates a function which properly handles the asynchronous behavior of
   * `com.github.benmanes.caffeine.cache.LoadingCache`.
   */
  def fromLoadingCache[K, V](cache: LoadingCache[K, Future[V]]): K => Future[V] = {
    val evicting = EvictingCache.lazily(new LoadingFutureCache(cache));
    { (key: K) => evicting.get(key).get.interruptible() }
  }

  /**
   * Creates a function which caches the results of `fn` in a
   * `com.github.benmanes.caffeine.cache.Cache`.
   */
  def fromCache[K, V](fn: K => Future[V], cache: Cache[K, Future[V]]): K => Future[V] =
    FutureCache.default(fn, new CaffeineCache(cache))
}
