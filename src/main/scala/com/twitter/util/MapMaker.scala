package com.twitter.util

import scala.collection.jcl.MapWrapper
import java.util.concurrent.TimeUnit
import com.google.common.collect.{MapMaker => GoogleMapMaker}
import com.google.common.base.Function

object MapMaker {
  def apply[K, V](f: Config[K, V] => Unit) = {
    val config = new Config[K, V]
    f(config)
    config()
  }

  class Config[K, V] {
    private var mapMaker = new GoogleMapMaker[K, V]
    private var valueOperation: Option[K => V] = None

    def weakKeys = mapMaker.weakKeys
    def weakValues = mapMaker.weakValues
    def softKeys = mapMaker.softKeys
    def softValues = mapMaker.softValues
    def concurrencyLevel(level: Int) = mapMaker.concurrencyLevel(level)
    def initialCapacity(capacity: Int) = mapMaker.initialCapacity(capacity)
    def expiration(expiration: Duration) = mapMaker.expiration(expiration.inMillis, TimeUnit.MILLISECONDS)
    def compute(_valueOperation: K => V) { valueOperation = Some(_valueOperation) }

    def apply() = {
      val javaMap = valueOperation map { valueOperation =>
        mapMaker.makeComputingMap[K, V](new Function[K, V] {
          def apply(k: K) = valueOperation(k)
        })
      } getOrElse(mapMaker.makeMap())
      new MapWrapper[K, V] {
        val underlying = javaMap
      }
    }
  }
}