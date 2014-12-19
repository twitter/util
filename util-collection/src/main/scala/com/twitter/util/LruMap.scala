package com.twitter.util

import java.{util => ju}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map, MapLike, SynchronizedMap}

import org.apache.commons.collections.map.LRUMap

/**
 * A wrapper trait for java.util.Map implementations to make them behave as scala Maps.
 * This is useful if you want to have more specifically-typed wrapped objects instead
 * of the generic maps returned by JavaConverters
 */
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

@deprecated("use scala.collection.JavaConverters instead", "2014/12/2")
case class JMapWrapper[A, B](underlying : ju.Map[A, B]) extends JMapWrapperLike[A, B, JMapWrapper[A, B]] {
  override def empty = JMapWrapper(new ju.HashMap[A, B])
}

object LruMap {
  def makeUnderlying[K, V](maxSize: Int) = new LRUMap(maxSize).asInstanceOf[ju.Map[K, V]]
}

/**
 * A scala `Map` backed by an [[org.apache.commons.collections.map.LRUMap]]
 */
class LruMap[K, V](val maxSize: Int, val underlying: ju.Map[K, V])
  extends JMapWrapperLike[K, V, LruMap[K, V]]
{
  override def empty: LruMap[K, V] = new LruMap[K, V](maxSize)
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}

/**
 * A synchronized scala `Map` backed by an [[org.apache.commons.collections.map.LRUMap]]
 */
class SynchronizedLruMap[K, V](maxSize: Int, underlying: ju.Map[K, V])
  extends LruMap[K, V](maxSize, ju.Collections.synchronizedMap(underlying))
  with SynchronizedMap[K, V]
{
  override def empty: SynchronizedLruMap[K, V] = new SynchronizedLruMap[K, V](maxSize)
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}
