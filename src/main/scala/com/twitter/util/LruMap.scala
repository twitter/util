package com.twitter.util

import org.apache.commons.collections.map.LRUMap
import scala.collection.jcl.MapWrapper
import scala.collection.mutable.SynchronizedMap
import java.util.Collections

class LruMap[K, V](val maxSize: Int) extends MapWrapper[K, V] {
  protected val unsynchronizedMap = new LRUMap(maxSize).asInstanceOf[java.util.Map[K, V]]
  val underlying = unsynchronizedMap
}

class SynchronizedLruMap[K, V](maxSize: Int) extends LruMap[K, V](maxSize) {
  override val underlying = Collections.synchronizedMap(unsynchronizedMap)
}
