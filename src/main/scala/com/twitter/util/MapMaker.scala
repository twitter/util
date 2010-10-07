package com.twitter.util

import java.util.concurrent.TimeUnit
import com.google.common.collect.{MapMaker => GoogleMapMaker}
import com.google.common.base.Function
import collection.JavaConversions.JConcurrentMapWrapper

object MapMaker {
  def apply[K, V](f: Config[K, V] => Unit) = {
    val config = new Config[K, V]
    f(config)
    config()
  }

  class Config[K, V] {
    private val mapMaker = new GoogleMapMaker
    private var valueOperation: Option[K => V] = None

    def weakKeys = { mapMaker.weakKeys; this }
    def weakValues = { mapMaker.weakValues; this }
    def softKeys = { mapMaker.softKeys; this }
    def softValues = { mapMaker.softValues; this }
    def concurrencyLevel(level: Int) = { mapMaker.concurrencyLevel(level); this }
    def initialCapacity(capacity: Int) = { mapMaker.initialCapacity(capacity); this }
    def expiration(expiration: Duration) = { mapMaker.expiration(expiration.inMillis, TimeUnit.MILLISECONDS); this }
    def compute(_valueOperation: K => V) = { valueOperation = Some(_valueOperation); this }

    def apply(): collection.mutable.ConcurrentMap[K, V] = {
      val javaMap = valueOperation map { valueOperation =>
        mapMaker.makeComputingMap[K, V](new Function[K, V] {
          def apply(k: K) = valueOperation(k)
        })
      } getOrElse(mapMaker.makeMap())
      new JConcurrentMapWrapper(javaMap) {
        // the default contains method (in 2.8) calls 'get' which fucks
        // with the compute method, so we need to override contains
        // to use containsKey
        override def contains(k: K) = underlying.containsKey(k)
      }
    }
  }
}
