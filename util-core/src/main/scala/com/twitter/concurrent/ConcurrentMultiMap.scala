// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import java.util.concurrent.ConcurrentSkipListMap

@deprecated("use guava's Multimaps.synchronizedMultimap", "6.2.x")
class ConcurrentMultiMap[K <% Ordered[K], V <% Ordered[V]] {
  class Container(k: K, v: Option[V])
    extends Product2[K, Option[V]]
    with Comparable[Container] {

    def key: K = k
    def value: Option[V] = v
    def _1: K = k
    def _2: Option[V] = v

    def isDefined: Boolean = value.isDefined

    def compareTo(that: Container): Int = key.compare(that.key) match {
      case 0 if this.isDefined && that.isDefined => value.get.compare(that.value.get)
      case 0 if !this.isDefined && !that.isDefined => 0
      case 0 if !this.isDefined => -1
      case 0 if !that.isDefined => 1

      case x => x
    }

    def canEqual(that: Any): Boolean = that.isInstanceOf[Container]

    override def equals(that: Any): Boolean =
      canEqual(that) && compareTo(that.asInstanceOf[Container]) == 0

    override def toString: String = s"($key, $value)"

    def swap: (Option[V], K) = Tuple2(value, key)
  }

  val underlying = new ConcurrentSkipListMap[Container, Unit]

  def +=(kv:(K, V)): Unit = {
    val (k, v) = kv
    underlying.putIfAbsent(new Container(k, Some(v)), ())
  }

  def get(k:K): List[V] = {
    def traverse(entry: Container): List[V] = {
      val nextEntry = underlying.higherKey(entry)
      if (nextEntry == null || nextEntry.key != k) {
        Nil
      } else {
        assert(nextEntry.value.isDefined)
        nextEntry.value.get :: traverse(nextEntry)
      }
    }

    traverse(new Container(k, None))
  }
}
