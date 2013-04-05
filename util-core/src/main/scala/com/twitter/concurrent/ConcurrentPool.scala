// Copyright 2010 Twitter, Inc.
//
// Concurrent object pool.

package com.twitter.concurrent

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

/**
 * The ConcurrentPool provides a concurrent object pool on top of the
 * java.util.concurrent primitives.
 *
 * The pool currently supports only FIFO ordering of items, and does
 * not yet clean up per-key object lists.
 */
@deprecated("use finagle's BufferingPool", "6.2.x")
class ConcurrentPool[K, V] {
  type Queue = ConcurrentLinkedQueue[V]
  type Map   = ConcurrentHashMap[K, Queue]

  val map = new Map
  val deathQueue = new ConcurrentLinkedQueue[Tuple2[K, Queue]]

  def doPut(k: K, v: V) {
    var objs = map.get(k)
    if (objs eq null) {
      map.putIfAbsent(k, new ConcurrentLinkedQueue[V])
      objs = map.get(k)
    }

    assert(objs ne null)
    objs.offer(v)
  }

  def put(k: K, v: V) {
    doPut(k, v)

    // Queue repair. TODO: amortize these more?
    var item = deathQueue.poll()
    while (item != null) {
      val (key, queue) = item
      if (!queue.isEmpty)
        queue.toArray().asInstanceOf[Array[V]].foreach(doPut(k, _))

      item = deathQueue.poll()
    }
  }

  def get(k: K): Option[V] = {
    val objs = map.get(k)
    if (objs eq null) {
      None
    } else {
      val obj = objs.poll()

      if (objs.isEmpty) {
        val deadMap = map.remove(k)
        if (deadMap ne null)
          deathQueue.offer((k, deadMap))
      }

      if (obj == null)
        None
      else
        Some(obj)
    }
  }
}
