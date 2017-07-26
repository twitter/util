package com.twitter.cache

import com.twitter.util.Future

object AsyncMemoize {

  /**
   * Produces a function which caches the result of an asynchronous
   * computation in the supplied Cache.
   *
   * Does not guarantee that `fn` will be computed exactly once for each key.
   */
  def apply[A, B](fn: A => Future[B], cache: FutureCache[A, B]): A => Future[B] =
    new MemoizedFunction(fn, cache)
}

private[cache] class MemoizedFunction[A, B](fn: A => Future[B], cache: FutureCache[A, B])
    extends (A => Future[B]) {

  def apply(a: A): Future[B] = cache.getOrElseUpdate(a) {
    fn(a)
  }
}
