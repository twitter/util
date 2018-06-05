package com.twitter.cache

import com.twitter.util.{Future, Throw}

private[cache] class EvictingCache[K, V](underlying: FutureCache[K, V])
    extends FutureCacheProxy[K, V](underlying) {
  private[this] def evictOnFailure(k: K, f: Future[V]): Future[V] = {
    f.onFailure { _ =>
      evict(k, f)
    }
    f // we return the original future to make evict(k, f) easier to work with.
  }

  override def set(k: K, v: Future[V]): Unit = {
    super.set(k, v)
    evictOnFailure(k, v)
  }

  override def getOrElseUpdate(k: K)(v: => Future[V]): Future[V] =
    evictOnFailure(k, underlying.getOrElseUpdate(k) {
      v
    })
}

private[cache] class LazilyEvictingCache[K, V](underlying: FutureCache[K, V])
    extends FutureCacheProxy[K, V](underlying) {
  private[this] def invalidateLazily(k: K, f: Future[V]): Unit = {
    f.poll match {
      case Some(Throw(e)) => underlying.evict(k, f)
      case _ =>
    }
  }

  override def get(k: K): Option[Future[V]] = {
    val result = super.get(k)
    result match {
      case Some(fut) => invalidateLazily(k, fut)
      case _ =>
    }
    result
  }

  override def getOrElseUpdate(k: K)(v: => Future[V]): Future[V] = {
    val result = super.getOrElseUpdate(k)(v)
    invalidateLazily(k, result)
    result
  }
}

object EvictingCache {

  /**
   * Wraps an underlying FutureCache, ensuring that failed Futures that are set in
   * the cache are evicted later.
   */
  def apply[K, V](underlying: FutureCache[K, V]): FutureCache[K, V] =
    new EvictingCache[K, V](underlying)

  /**
   * Wraps an underlying FutureCache, ensuring that if a failed future
   * is fetched, we evict it.
   */
  def lazily[K, V](underlying: FutureCache[K, V]): FutureCache[K, V] =
    new LazilyEvictingCache[K, V](underlying)
}
