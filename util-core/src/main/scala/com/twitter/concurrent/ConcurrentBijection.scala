// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.{Map => MMap}
import scala.collection.JavaConversions._

// A bijection that may be modified and accessed simultaneously.  Note
// that we can allow only one modification at a time. Updates need to
// be serialized to ensure that the bijective property is maintained.
class ConcurrentBijection[A, B] extends MMap[A, B] {
  val forward = new ConcurrentHashMap[A, B]
  val reverse = new ConcurrentHashMap[B, A]

  def toOpt[T](x: T) = if (x == null) None else Some(x)

  def -=(key: A) = {
    synchronized {
      val value = forward.remove(key)
      if (value != null)
        reverse.remove(value)
    }
    this
  }

  def +=(elem: (A, B)) = { 
    elem match {
      case (key, value) => update(key, value)
    }
    this
  }

  override def update(key: A, value: B) = synchronized {
    // We need to update:
    //
    //    a -> b
    //    b -> a
    //
    // There may be existing mappings:
    //
    //   a  -> b'
    //   b' -> a
    //
    // or
    //
    //   a' -> b
    //   b  -> a'
    //
    // So we need to ensure these are killed.
    val oldValue = forward.put(key, value)
    if (oldValue != value) {
      if (oldValue != null) {
        // Remove the old reverse mapping.
        val keyForOldValue = reverse.remove(oldValue)
        if (key != keyForOldValue)
          forward.remove(keyForOldValue)
      }

      val oldKeyForValue = reverse.put(value, key)
      if (oldKeyForValue != null)
        forward.remove(oldKeyForValue)
    }
  }

  override def size = forward.size

  def get(key: A) = toOpt(forward.get(key))
  def getReverse(value: B) = toOpt(reverse.get(value))

  def iterator = forward.entrySet.iterator.map(e => (e.getKey, e.getValue))
}
