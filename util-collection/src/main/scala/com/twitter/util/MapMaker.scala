package com.twitter.util

import collection.JavaConversions.JConcurrentMapWrapper
import collection.mutable.{Buffer, ListBuffer}
import com.google.common.cache.{LoadingCache,
  RemovalListener, RemovalNotification, Cache, CacheLoader, CacheBuilder}
import java.util.concurrent.TimeUnit.{MILLISECONDS => MS}

object MapMaker {
  @deprecated("Use the Guava cachebuilder directly.")
  def apply[K, V](f: Config[K, V] => Unit) = {
    val config = new Config[K, V]
    f(config)
    config()
  }

  class Config[K, V] {
    private val cacheBuilder = CacheBuilder.newBuilder().asInstanceOf[CacheBuilder[K, V]]
    private var valueOperation: Option[K => V] = None
    private var removalListener: Option[RemovalListener[K, V]] = None

    def weakKeys = { cacheBuilder.weakKeys; this }
    def weakValues = { cacheBuilder.weakValues; this }
    def softValues = { cacheBuilder.softValues; this }
    def concurrencyLevel(level: Int) = { cacheBuilder.concurrencyLevel(level); this }
    def initialCapacity(capacity: Int) = { cacheBuilder.initialCapacity(capacity); this }
    def expiration(expiration: Duration) = { cacheBuilder.expireAfterWrite(expiration.inMillis, MS); this }
    def compute(_valueOperation: K => V) = { valueOperation = Some(_valueOperation); this }
    def expireAfterAccess(ttl: Duration) = { cacheBuilder.expireAfterAccess(ttl.inMillis, MS); this }
    def expireAfterWrite(ttl: Duration) = { cacheBuilder.expireAfterWrite(ttl.inMillis, MS); this }
    def maximumSize(size: Int) = { cacheBuilder.maximumSize(size); this }

    /**
     * Sets this MapMaker's eviction listener. The eviction listener is called in
     * the same thread as what caused the eviction.
     */
    def withEvictionListener(f: (K, V) => Unit): Config[K, V] = {
      removalListener = Some(
        new RemovalListener[K, V] {
          def onRemoval(n: RemovalNotification[K, V]) = f(n.getKey, n.getValue)
        }
      )
      this
    }

    def apply(): collection.mutable.ConcurrentMap[K, V] = {
      val cacheBuilderWithListener = removalListener match {
        case Some(l) => cacheBuilder.removalListener(l)
        case None => cacheBuilder
      }

      valueOperation match {
        case Some(op) => mkComputable(op, cacheBuilderWithListener)
        case None => mkStandard(cacheBuilderWithListener)
      }
    }

    private[this] def mkComputable(op: K => V, builder: CacheBuilder[K, V]) = {
      val cache: LoadingCache[K, V] = builder.build[K, V](
        new CacheLoader[K, V] {
          def load(key: K) = op(key)
        }
      )

      new JConcurrentMapWrapper(cache.asMap()) {
        // the default contains method (in 2.8) calls 'get' which messes
        // with the compute method, so we need to override contains
        // to use containsKey
        override def contains(k: K) = underlying.containsKey(k)

        // We need to override get in order to trigger computation: asMap
        // returns a view, but it does not support computing values.
        override def get(k: K) = Option(cache.get(k))
      }
    }

    private[this] def mkStandard(builder: CacheBuilder[K, V]) = {
      val cache: Cache[K, V] = builder.build[K, V]()
      new JConcurrentMapWrapper(cache.asMap())
    }
  }
}
