package com.twitter.util

import scala.collection.SortedMap

/**
 *  @author Oscar Boykin
 *
 * Immutable implementation of an LRU cache.
 */

object ImmutableLRU {
  def apply[K,V](maxSize: Int) = {
    new ImmutableLRU(maxSize, 0, Map.empty[K,(Long,V)], SortedMap.empty[Long,K])
  }
}

/**
 * "map" is the backing store used to hold key->(index,value)
 * pairs. The index tracks the access time for a particular key. "ord"
 * is used to determine the Least-Recently-Used key in "map" by taking
 * the minimum index.
 */
class ImmutableLRU[K,V] private (maxSize: Int, idx: Long, map: Map[K,(Long,V)], ord: SortedMap[Long,K]) {

  // Scala's SortedMap requires a key ordering; ImmutableLRU doesn't
  // care about pulling a minimum value out of the SortedMap, so the
  // following kOrd treats every value as equal.
  protected implicit val kOrd = new Ordering[K] { def compare(l:K,r:K) = 0 }

  def size: Int = map.size

  def keySet = map.keySet

  // Put in and return the Key it evicts and the new LRU
  def +(kv: (K,V)): (Option[K], ImmutableLRU[K,V]) = {
    val (key, value) = kv
    val newIdx = idx + 1
    val newMap = map + (key -> (newIdx, value))
    // Now update the ordered cache:
    val baseOrd = map.get(key).map { case (id, _) => ord - id }.getOrElse(ord)
    val ordWithNewKey = baseOrd + (newIdx -> key)
    // Do we need to remove an old key:
    val (evicts, finalMap, finalOrd) = if(ordWithNewKey.size > maxSize) {
      val (minIdx, eKey) = ordWithNewKey.min
      (Some(eKey), newMap - eKey, ordWithNewKey - minIdx)
    }
    else {
      (None, newMap, ordWithNewKey)
    }
    (evicts, new ImmutableLRU[K,V](maxSize, newIdx, finalMap, finalOrd))
  }
  def get(k: K): (Option[V], ImmutableLRU[K,V]) = {
    val (optionalValue, lru) = remove(k)
    val newLru = optionalValue.map { v => (lru + (k -> v))._2 } getOrElse(lru)
    (optionalValue, newLru)
  }

  // If the key is present in the cache, returns the pair of
  // Some(value) and the cache with the key removed. Else, returns
  // None and the unmodified cache.
  def remove(k: K): (Option[V], ImmutableLRU[K,V]) =
    map.get(k).map { case (kidx, v) =>
      val newMap = map - k
      val newOrd = ord - kidx
      // Note we don't increase the idx on a remove, only on put:
      (Some(v), new ImmutableLRU[K,V](maxSize, idx, newMap, newOrd))
    }.getOrElse( (None, this) )

  override def toString = { "ImmutableLRU(" + map.toList.mkString(",") + ")" }
}
