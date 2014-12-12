package com.twitter.cache.guava

import com.google.common.cache.{CacheLoader, CacheBuilder}
import com.twitter.cache.AbstractFutureCacheTest
import com.twitter.util.{Future, Promise}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GuavaCacheTest extends AbstractFutureCacheTest {
  def name: String = "GuavaCache"

  def mkCtx(): Ctx = new Ctx {
    val guava = CacheBuilder.newBuilder().build[String, Future[String]]()
    val cache = new GuavaCache[String, String](guava)
  }

  def mkCache() =
    CacheBuilder
      .newBuilder()
      .build(
        new CacheLoader[String, Future[Int]] {
          override def load(k: String): Future[Int] = new Promise[Int]
        }
      )

  test("GuavaCache#fromLoadingCache is interrupt safe") {
    val fCache = GuavaCache.fromLoadingCache(mkCache())
    interruptSafe(fCache)
  }

  test("GuavaCache#fromCache is interrupt safe") {
    val fCache = GuavaCache.fromCache((_:String) => new Promise[Int], mkCache())
    interruptSafe(fCache)
  }

  def interruptSafe(fCache: (String => Future[Int])) {
    val f = fCache("key")
    val exn = new Exception
    f.raise(exn)

    val f2 = fCache("key")
    val p = new Promise[Int]
    p.become(f2)

    assert(p.isInterrupted === None)
  }
}
