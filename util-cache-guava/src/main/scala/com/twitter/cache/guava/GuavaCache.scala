package com.twitter.cache.guava

import com.twitter.cache._
import com.google.common.cache.{Cache => GCache, LoadingCache}
import com.twitter.util.Future
import java.util.concurrent.Callable

/**
 * A [[com.twitter.cache.FutureCache]] backed by a
 * [[com.google.common.cache.Cache]].
 *
 * Any correct implementation should make sure that you evict failed results,
 * and don't interrupt the underlying request that has been fired off.
 * [[EvictingCache]] and [[Future#interrupting]] are useful tools for building
 * correct FutureCaches.  A reference implementation for caching the results of
 * an asynchronous function with a guava Cache can be found at
 * [[GuavaCache$.fromCache]].
 */
class GuavaCache[K, V](cache: GCache[K, Future[V]]) extends ConcurrentMapCache[K, V](cache.asMap) {
  override def getOrElseUpdate(k: K)(v: => Future[V]): Future[V] =
    cache.get(
      k,
      new Callable[Future[V]] {
        def call(): Future[V] = v
      })
}

/**
 * A [[com.twitter.cache.FutureCache]] backed by a
 * [[com.google.common.cache.LoadingCache]].

 * Any correct implementation should make sure that you evict failed results,
 * and don't interrupt the underlying request that has been fired off.
 * [[EvictingCache]] and [[Future#interrupting]] are useful tools for building
 * correct FutureCaches.  A reference implementation for caching the results of
 * an asynchronous function with a guava LoadingCache can be found at
 * [[GuavaCache$.fromLoadingCache]].
 */
class LoadingFutureCache[K, V](cache: LoadingCache[K, Future[V]])
    extends GuavaCache[K, V](cache)
    with (K => Future[V]) {
  // the contract for LoadingCache is that it can't return null from get.
  def apply(key: K): Future[V] = cache.get(key)

  override def get(key: K): Option[Future[V]] = Some(apply(key))
}

object GuavaCache {

  /**
   * Creates a function which properly handles the asynchronous behavior of
   * [[com.google.common.cache.LoadingCache]].
   */
  def fromLoadingCache[K, V](cache: LoadingCache[K, Future[V]]): K => Future[V] = {
    val evicting = EvictingCache.lazily(new LoadingFutureCache(cache));
    { key: K => evicting.get(key).get.interruptible() }
  }

  /**
   * Creates a function which caches the results of `fn` in a
   * [[com.google.common.cache.Cache]].
   */
  def fromCache[K, V](fn: K => Future[V], cache: GCache[K, Future[V]]): K => Future[V] =
    FutureCache.default(fn, new GuavaCache(cache))

}
