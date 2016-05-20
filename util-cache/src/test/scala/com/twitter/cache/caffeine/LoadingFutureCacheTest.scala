package com.twitter.cache.caffeine

import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader}
import com.twitter.cache.AbstractLoadingFutureCacheTest
import com.twitter.util.Future
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LoadingFutureCacheTest extends AbstractLoadingFutureCacheTest {

  def name: String = "LoadingFutureCache (caffeine)"

  // NB we can't reuse AbstractFutureCacheTest since
  // loading cache semantics are sufficiently unique
  // to merit distinct tests.

  def mkCtx: Ctx = new Ctx  {
    val cache = new LoadingFutureCache(
      Caffeine
        .newBuilder()
        .build(
          new CacheLoader[String,Future[Int]] {
            override def load(k: String): Future[Int] = {
              cacheLoaderCount += 1
              Future.value(k.hashCode)
            }
          }
        )
    )
  }
}
