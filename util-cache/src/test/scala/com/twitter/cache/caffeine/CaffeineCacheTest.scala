package com.twitter.cache.caffeine

import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}
import com.twitter.cache.AbstractFutureCacheTest
import com.twitter.util.{Future, Promise}

class CaffeineCacheTest extends AbstractFutureCacheTest {
  def name: String = "CaffeineCache"

  def mkCtx(): Ctx = new Ctx {
    val caffeine = Caffeine.newBuilder().build[String, Future[String]]()
    val cache = new CaffeineCache[String, String](caffeine)
  }

  def mkCache(): LoadingCache[String, Future[Int]] =
    Caffeine
      .newBuilder()
      .build(
        new CacheLoader[String, Future[Int]] {
          override def load(k: String): Future[Int] = new Promise[Int]
        }
      )

  test("CaffeineCache#fromLoadingCache is interrupt safe") {
    val fCache = CaffeineCache.fromLoadingCache(mkCache())
    interruptSafe(fCache)
  }

  test("CaffeineCache#fromCache is interrupt safe") {
    val fCache = CaffeineCache.fromCache((_: String) => new Promise[Int], mkCache())
    interruptSafe(fCache)
  }
}
