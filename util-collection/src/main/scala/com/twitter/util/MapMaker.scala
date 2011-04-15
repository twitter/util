package com.twitter.util

import java.util.concurrent.TimeUnit.{MILLISECONDS => MS}
import com.google.common.collect.{MapMaker => GoogleMapMaker}
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
    def expiration(expiration: Duration) = { mapMaker.expiration(expiration.inMillis, MS); this }
    def compute(_valueOperation: K => V) = { valueOperation = Some(_valueOperation); this }
    def expireAfterAccess(ttl: Duration) = { mapMaker.expireAfterAccess(ttl.inMillis, MS); this }
    def expireAfterWrite(ttl: Duration) = { mapMaker.expireAfterWrite(ttl.inMillis, MS); this }
    def maximumSize(size: Int) = { mapMaker.maximumSize(size); this }

    def apply(): collection.mutable.ConcurrentMap[K, V] = {
      val javaMap = valueOperation map { valueOperation =>
        mapMaker.makeComputingMap[K, V](new com.google.common.base.Function[K, V] {
          def apply(k: K) = valueOperation(k)
        })
      } getOrElse(mapMaker.makeMap())
      new JConcurrentMapWrapper(javaMap) {
        // the default contains method (in 2.8) calls 'get' which messes
        // with the compute method, so we need to override contains
        // to use containsKey
        override def contains(k: K) = underlying.containsKey(k)
      }
    }
  }
}
