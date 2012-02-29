package com.twitter.util

import scala.collection.mutable.{Map, Set}
import java.util.concurrent.TimeUnit
import collection.JavaConversions.JConcurrentMapWrapper
import com.google.common.collect.{MapMaker => GoogleMapMaker}

object SetMaker {
  def apply[A](f: Config[A] => Any): Set[A] = {
    val config = new Config[A]
    f(config)
    config()
  }

  class Config[A] {
    private val mapMaker = new GoogleMapMaker

    def weakValues = { mapMaker.weakKeys; mapMaker.weakValues; this }
    @deprecated("softValues is deprecated in Guava11 - use CacheBuilder")
    def softValues = { mapMaker.softKeys; mapMaker.softValues; this }
    def concurrencyLevel(level: Int) = { mapMaker.concurrencyLevel(level); this }
    def initialCapacity(capacity: Int) = { mapMaker.initialCapacity(capacity); this }
    @deprecated("Expiration is deprecated in Guava11 - use CacheBuilder")
    def expiration(expiration: Duration) = { mapMaker.expiration(expiration.inMillis, TimeUnit.MILLISECONDS); this }

    def apply() = new MapToSetAdapter(
      new JConcurrentMapWrapper[A, A](mapMaker.makeMap()))
  }
}

class MapToSetAdapter[A](map: Map[A, A]) extends Set[A] {
  def +=(elem: A) = {
    map(elem) = elem
    this
  }
  def -=(elem: A) = {
    map -= elem
    this
  }
  override def size = map.size
  def iterator = map.keysIterator
  def contains(elem: A) = map.contains(elem)
}
