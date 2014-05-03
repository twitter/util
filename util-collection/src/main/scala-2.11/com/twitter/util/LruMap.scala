package com.twitter.util

import org.apache.commons.collections.map.LRUMap
import collection.convert.Wrappers.JMapWrapper
import scala.collection.mutable.SynchronizedMap
import java.util

object LruMap {
  def makeUnderlying[K, V](maxSize: Int) = new LRUMap(maxSize).asInstanceOf[util.Map[K, V]]
}

class LruMap[K, V](val maxSize: Int, underlying: util.Map[K, V])
  extends JMapWrapper[K, V](underlying)
{
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}

class SynchronizedLruMap[K, V](maxSize: Int, underlying: util.Map[K, V])
  extends LruMap[K, V](maxSize, util.Collections.synchronizedMap(underlying))
  with SynchronizedMap[K, V]
{
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}
