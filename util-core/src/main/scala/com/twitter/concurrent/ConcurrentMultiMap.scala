// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import java.util.concurrent.ConcurrentSkipListMap

@deprecated("use guava's Multimaps.synchronizedMultimap", "6.2.x")
class ConcurrentMultiMap[K <% Ordered[K], V <% Ordered[V]] {
  class Container(k: K, v: Option[V])
  // TODO: extending tupes is deprecated and will be removed in the next version.
  // Remove this inheritance in the next major version
  extends Tuple2[K, Option[V]](k, v)
  with Comparable[Container]
  {
    def key   = k
    def value = v

    def isDefined = value.isDefined

    def compareTo(that: Container) = this.key.compare(that.key) match {
      case 0 if ( this.isDefined &&  that.isDefined) => this.value.get.compare(that.value.get)
      case 0 if (!this.isDefined && !that.isDefined) => 0
      case 0 if (!this.isDefined)                    => -1
      case 0 if (!that.isDefined)                    => 1

      case x => x
    }
  }

  val underlying = new ConcurrentSkipListMap[Container, Unit]

  def +=(kv:(K, V)) {
    val (k, v) = kv
    underlying.putIfAbsent(new Container(k, Some(v)), ())
  }

  def get(k:K):List[V] = {
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

