package com.twitter.util

import java.{util => ju}
import java.util.LinkedHashMap
import scala.collection.JavaConverters._
import scala.collection.mutable.{Map, MapLike}

/**
 * A wrapper trait for java.util.Map implementations to make them behave as scala Maps.
 * This is useful if you want to have more specifically-typed wrapped objects instead
 * of the generic maps returned by JavaConverters
 */
trait JMapWrapperLike[A, B, +Repr <: MapLike[A, B, Repr] with Map[A, B]]
    extends Map[A, B]
    with MapLike[A, B, Repr] {
  def underlying: ju.Map[A, B]

  override def size = underlying.size

  override def get(k: A) = underlying.asScala.get(k)

  override def +=(kv: (A, B)): this.type = { underlying.put(kv._1, kv._2); this }
  override def -=(key: A): this.type = { underlying remove key; this }

  override def put(k: A, v: B): Option[B] = underlying.asScala.put(k, v)

  override def update(k: A, v: B): Unit = { underlying.put(k, v) }

  override def remove(k: A): Option[B] = underlying.asScala.remove(k)

  override def clear() = underlying.clear()

  override def empty: Repr = null.asInstanceOf[Repr]

  override def iterator = underlying.asScala.iterator
}

object LruMap {

  // initial capacity and load factor are the normal defaults for LinkedHashMap
  def makeUnderlying[K, V](maxSize: Int): ju.Map[K, V] =
    new LinkedHashMap[K, V](
      16, /* initial capacity */
      0.75f, /* load factor */
      true /* access order (as opposed to insertion order) */
    ) {
      override protected def removeEldestEntry(eldest: ju.Map.Entry[K, V]): Boolean = {
        this.size() > maxSize
      }
    }
}

/**
 * A scala `Map` backed by a [[java.util.LinkedHashMap]]
 */
class LruMap[K, V](val maxSize: Int, val underlying: ju.Map[K, V])
    extends JMapWrapperLike[K, V, LruMap[K, V]] {
  override def empty: LruMap[K, V] = new LruMap[K, V](maxSize)
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}

/**
 * A synchronized scala `Map` backed by an [[java.util.LinkedHashMap]]
 */
class SynchronizedLruMap[K, V](maxSize: Int, underlying: ju.Map[K, V])
    extends LruMap[K, V](maxSize, ju.Collections.synchronizedMap(underlying)) {
  override def get(key: K): Option[V] = synchronized { super.get(key) }
  override def iterator: Iterator[(K, V)] = synchronized { super.iterator }
  override def +=(kv: (K, V)): this.type = synchronized[this.type] { super.+=(kv) }
  override def -=(key: K): this.type = synchronized[this.type] { super.-=(key) }

  override def size: Int = synchronized { super.size }
  override def put(key: K, value: V): Option[V] = synchronized { super.put(key, value) }
  override def update(key: K, value: V): Unit = synchronized { super.update(key, value) }
  override def remove(key: K): Option[V] = synchronized { super.remove(key) }
  override def clear(): Unit = synchronized { super.clear() }
  override def getOrElseUpdate(key: K, default: => V): V = synchronized {
    super.getOrElseUpdate(key, default)
  }
  override def transform(f: (K, V) => V): this.type = synchronized[this.type] { super.transform(f) }
  override def retain(p: (K, V) => Boolean): this.type = synchronized[this.type] { super.retain(p) }
  override def values: scala.collection.Iterable[V] = synchronized { super.values }
  override def valuesIterator: Iterator[V] = synchronized { super.valuesIterator }
  override def clone(): Self = synchronized { super.clone() }
  override def foreach[U](f: ((K, V)) => U) = synchronized { super.foreach(f) }
  override def apply(key: K): V = synchronized { super.apply(key) }
  override def keySet: scala.collection.Set[K] = synchronized { super.keySet }
  override def keys: scala.collection.Iterable[K] = synchronized { super.keys }
  override def keysIterator: Iterator[K] = synchronized { super.keysIterator }
  override def isEmpty: Boolean = synchronized { super.isEmpty }
  override def contains(key: K): Boolean = synchronized { super.contains(key) }
  override def isDefinedAt(key: K) = synchronized { super.isDefinedAt(key) }
  override def empty: SynchronizedLruMap[K, V] = new SynchronizedLruMap[K, V](maxSize)
  //!!! todo: also add all other methods
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}
