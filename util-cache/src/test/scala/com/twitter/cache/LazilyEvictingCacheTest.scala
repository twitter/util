package com.twitter.cache

import com.github.benmanes.caffeine.cache.{CacheLoader, Caffeine}
import com.twitter.cache.caffeine.LoadingFutureCache
import com.twitter.util.{Await, Future, Promise}
import org.mockito.Matchers._
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar

class LazilyEvictingCacheTest extends FunSuite with MockitoSugar {
  private val explodingCacheLoader =
    new CacheLoader[String, Future[String]] {
      def load(k: String): Future[String] =
        throw new RuntimeException("unexpected load call")
    }

  test("LazilyEvictingCache should evict on failed futures for set") {
    val cache = mock[LoadingFutureCache[String, String]]
    val fCache = EvictingCache.lazily(cache)
    val p = new Promise[String]()
    when(cache.get("key")).thenReturn(Some(p))
    fCache.set("key", p)
    verify(cache).set("key", p)

    val exn = new Exception
    p.setException(exn)

    // don't evict the key until subsequent lookup
    verify(cache, never).evict(any[String], any[Future[String]])

    val Some(failed) = fCache.get("key")
    val thrown = intercept[Exception] { Await.result(failed) }
    assert(thrown == exn)

    verify(cache).evict("key", p)
  }

  test("LazilyEvictingCache should keep satisfied futures for set") {
    val cache = mock[LoadingFutureCache[String, String]]
    val fCache = new LazilyEvictingCache(cache)
    val p = new Promise[String]()
    when(cache.get("key")).thenReturn(Some(p))

    fCache.set("key", p)
    verify(cache).set("key", p)

    p.setValue("value")

    val Some(res) = fCache.get("key")
    assert(Await.result(res) == "value")
    verify(cache, never).evict("key", p)
  }

  test("LazilyEvictingCache getOrElseUpdate doesn't mutate previously set values") {
    val cache = new LoadingFutureCache(
      Caffeine.newBuilder().build(explodingCacheLoader)
    )
    val fCache = new LazilyEvictingCache(cache)

    fCache.set("key", Future.value("value"))
    val res = fCache.getOrElseUpdate("key") {
      throw new RuntimeException("unexpected set")
    }
    assert(Await.result(res) == "value")
  }

  test("LazilyEvictingCache getOrElseUpdate computes a future") {
    val cache = new LoadingFutureCache(
      Caffeine.newBuilder().build(explodingCacheLoader)
    )
    val fCache = new LazilyEvictingCache(cache)

    val p = new Promise[String]()
    val f = fCache.getOrElseUpdate("key")(p)

    p.setValue("new value")
    assert(Await.result(f) == "new value")
    val Some(f2) = fCache.get("key")
    assert(Await.result(f2) == "new value")
  }

  test("LazilyEvictingCache should evict on failed futures for getOrElseUpdate") {
    val p = new Promise[Int]()

    var loadCount = 0
    val cache = new LoadingFutureCache(
      Caffeine
        .newBuilder()
        .build(
          new CacheLoader[String, Future[Int]] {
            def load(k: String): Future[Int] = {
              loadCount += 1
              Future.value(loadCount)
            }
          }
        )
    )
    val fCache = new LazilyEvictingCache(cache)

    assert(fCache.getOrElseUpdate("key")(p).poll == p.poll)
    val exn = new Exception
    p.setException(exn)

    // first lookup returns the failed Future
    val Some(x) = fCache.get("key")
    val thrown = intercept[Exception] { Await.result(x) }
    assert(thrown == exn)

    // second lookup returns the reloaded value after
    // the previous value is invalidated
    val Some(y) = fCache.get("key")
    assert(Await.result(y) == 1)
    assert(loadCount == 1)
  }

  test("LazilyEvictingCache should keep satisfied futures for getOrElseUpdate") {
    val p = new Promise[Int]()

    var loadCount = 0
    val cache = new LoadingFutureCache(
      Caffeine
        .newBuilder()
        .build(
          new CacheLoader[String, Future[Int]] {
            def load(k: String): Future[Int] = {
              loadCount += 1
              Future.value(loadCount)
            }
          }
        )
    )
    val fCache = new LazilyEvictingCache(cache)

    assert(fCache.getOrElseUpdate("key")(p).poll == p.poll)
    val Some(x) = fCache.get("key")
    assert(!x.isDefined)

    p.setValue(12345)
    val Some(y) = fCache.get("key")
    assert(Await.result(y) == 12345)
    assert(loadCount == 0)
  }
}
