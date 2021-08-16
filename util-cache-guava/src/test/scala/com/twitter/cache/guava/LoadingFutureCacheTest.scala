package com.twitter.cache.guava

import com.google.common.cache.{CacheLoader, CacheBuilder}
import com.twitter.cache.AbstractLoadingFutureCacheTest
import com.twitter.util.Future

class LoadingFutureCacheTest extends AbstractLoadingFutureCacheTest {

  def name: String = "LoadingFutureCache (guava)"

  // NB we can't reuse AbstractFutureCacheTest since
  // loading cache semantics are sufficiently unique
  // to merit distinct tests.

  def mkCtx(): Ctx = new Ctx {
    val cache = new LoadingFutureCache(
      CacheBuilder
        .newBuilder()
        .build(
          new CacheLoader[String, Future[Int]] {
            override def load(k: String): Future[Int] = {
              cacheLoaderCount += 1
              Future.value(k.hashCode)
            }
          }
        )
    )
  }
}
