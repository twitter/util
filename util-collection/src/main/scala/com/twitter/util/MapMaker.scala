package com.twitter.util

import java.util.concurrent.TimeUnit.{MILLISECONDS => MS}
import com.google.common.collect.{MapMaker => GoogleMapMaker, MapEvictionListener}
import collection.JavaConversions.JConcurrentMapWrapper
import collection.mutable.{Buffer, ListBuffer}

object MapMaker {
  def apply[K, V](f: Config[K, V] => Unit) = {
    val config = new Config[K, V]
    f(config)
    config()
  }

  class Config[K, V] {
    private val mapMaker = new GoogleMapMaker
    private var valueOperation: Option[K => V] = None
    private var evictionListener: Option[MapEvictionListener[K, V]] = None

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

    /**
     * Sets this MapMaker's eviction listener. The eviction listener is called in
     * the same thread as what caused the eviction.
     */
    def withEvictionListener(f: (K, V) => Unit): Config[K, V] = {
      evictionListener = Some(
        new MapEvictionListener[K, V] {
          def onEviction(key: K, value: V) = f(key, value)
        }
      )
      this
    }

    def apply(): collection.mutable.ConcurrentMap[K, V] = {
      val mapMakerWithListener = evictionListener match {
        case Some(l) => mapMaker.evictionListener(l).asInstanceOf[GoogleMapMaker]
        case None => mapMaker
      }

      val javaMap = valueOperation map { valueOperation =>
        mapMakerWithListener.makeComputingMap[K, V](new com.google.common.base.Function[K, V] {
          def apply(k: K) = valueOperation(k)
        })
      } getOrElse(mapMakerWithListener.makeMap())

      new JConcurrentMapWrapper(javaMap) {
        // the default contains method (in 2.8) calls 'get' which messes
        // with the compute method, so we need to override contains
        // to use containsKey
        override def contains(k: K) = underlying.containsKey(k)
      }
    }
  }
}
