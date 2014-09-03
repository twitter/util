package com.twitter.cache

import com.twitter.util.Future

/**
 * Encodes keys, producing a Cache that takes keys of a different type.
 *
 * Useful for compressing keys, or discarding extra information.  This is
 * especially useful when information is useful for computing a value for the
 * first time, but isn't necessary for demonstrating distinctness.
 *
 * e.g.
 *
 * val fibCache: FutureCache[Int, Int]
 * val fn: (Int, Int, Int, Int) => Int = { case (target, prev, cur, idx) =>
 *   if (idx == target) cur else fn((target, cur, prev + cur, idx + 1))
 * }
 * val memo: (Int, Int, Int, Int) => Int = Cache(fn, new KeyEncodingCache(
 *   new FutureCache[Int, Int],
 *   { case (target: Int, _, _, _) => target }
 * ))
 */
private[cache] class KeyEncodingCache[K, V, U](
  encode: K => V,
  underlying: FutureCache[V, U]
) extends FutureCache[K, U] {
  override def get(key: K): Option[Future[U]] = underlying.get(encode(key))

  def set(key: K, value: Future[U]): Unit = underlying.set(encode(key), value)

  def getOrElseUpdate(key: K)(compute: => Future[U]): Future[U] =
    underlying.getOrElseUpdate(encode(key))(compute)

  def evict(key: K, value: Future[U]): Boolean = underlying.evict(encode(key), value)

  def size: Int = underlying.size
}
