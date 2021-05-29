package com.twitter.cache

import com.twitter.util.{Promise, Future}
import java.util.concurrent.ConcurrentMap

/**
 * A [[com.twitter.cache.FutureCache]] backed by a
 * `java.util.concurrent.ConcurrentMap`
 *
 * Any correct implementation should make sure that you evict failed
 * results, and don't interrupt the underlying request that has been
 * fired off.  [[EvictingCache$]] and interrupting [[com.twitter.util.Future]]s are
 * useful tools for building correct FutureCaches.  A reference
 * implementation for caching the results of an asynchronous function
 * with a `ConcurrentMap` can be found at [[FutureCache$.fromMap]].
 */
class ConcurrentMapCache[K, V](underlying: ConcurrentMap[K, Future[V]]) extends FutureCache[K, V] {
  def get(key: K): Option[Future[V]] = Option(underlying.get(key))

  def set(key: K, value: Future[V]): Unit = underlying.put(key, value)

  def getOrElseUpdate(key: K)(compute: => Future[V]): Future[V] = {
    val p = Promise[V]()
    underlying.putIfAbsent(key, p) match {
      case null =>
        p.become(compute)
        p
      case oldv => oldv
    }
  }

  def evict(key: K, value: Future[V]): Boolean = underlying.remove(key, value)

  def size: Int = underlying.size()
}
