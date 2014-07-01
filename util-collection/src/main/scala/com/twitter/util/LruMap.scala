package com.twitter.util

import java.{util => ju}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map, MapLike, SynchronizedMap}

import org.apache.commons.collections.map.LRUMap

trait JMapWrapperLike[A, B, +Repr <: MapLike[A, B, Repr] with Map[A, B]] extends Map[A, B] with MapLike[A, B, Repr] {
  def underlying: ju.Map[A, B]

  override def size = underlying.size

  override def get(k: A) = underlying.asScala.get(k)

  override def +=(kv: (A, B)): this.type = { underlying.put(kv._1, kv._2); this }
  override def -=(key: A): this.type = { underlying remove key; this }

  override def put(k: A, v: B): Option[B] = underlying.asScala.put(k, v)

  override def update(k: A, v: B) { underlying.put(k, v) }

  override def remove(k: A): Option[B] = underlying.asScala.remove(k)

  override def clear() = underlying.clear()

  override def empty: Repr = null.asInstanceOf[Repr]

  override def iterator = underlying.asScala.iterator
}

case class JMapWrapper[A, B](underlying : ju.Map[A, B]) extends JMapWrapperLike[A, B, JMapWrapper[A, B]] {
  override def empty = JMapWrapper(new ju.HashMap[A, B])
}

object LruMap {
  def makeUnderlying[K, V](maxSize: Int) = new LRUMap(maxSize).asInstanceOf[ju.Map[K, V]]
}

class LruMap[K, V](val maxSize: Int, underlying: ju.Map[K, V])
  extends JMapWrapper[K, V](underlying)
{
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}

class SynchronizedLruMap[K, V](maxSize: Int, underlying: ju.Map[K, V])
  extends LruMap[K, V](maxSize, ju.Collections.synchronizedMap(underlying))
  with SynchronizedMap[K, V]
{
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}
