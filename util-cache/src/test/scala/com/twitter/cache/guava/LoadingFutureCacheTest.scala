package com.twitter.cache.guava

import com.google.common.cache.{CacheLoader, CacheBuilder}
import com.twitter.util.{Await, Future}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LoadingFutureCacheTest extends FunSuite {

  def name: String = "LoadingFutureCache"

  // NB we can't reuse AbstractFutureCacheTest since
  // loading cache semantics are sufficiently unique
  // to merit distinct tests.

  trait Ctx  {
    var cacheLoaderCount = 0
    val cache = new LoadingFutureCache(
      CacheBuilder
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

  test("return CacheLoader result for unset keys") {
    val ctx = new Ctx {}
    import ctx._
    val Some(res) = cache.get("key")
    assert(Await.result(res) === "key".hashCode)
    assert(cacheLoaderCount === 1)
  }

  test("call CacheLoader one time for non-evicted keys") {
    val ctx = new Ctx {}
    import ctx._
    val Some(res) = cache.get("key")
    val Some(res2) = cache.get("key")
    val Some(res3) = cache.get("key")
    assert(Await.result(res) === "key".hashCode)
    assert(Await.result(res2) === "key".hashCode)
    assert(Await.result(res3) === "key".hashCode)
    assert(cacheLoaderCount === 1)
  }

  test("return set values") {
    val ctx = new Ctx {}
    import ctx._

    cache.set("key", Future.value(1234))
    val Some(res) = cache.get("key")
    val res2 = cache("key")

    assert(Await.result(res) === 1234)
    assert(Await.result(res2) === 1234)
    assert(cacheLoaderCount === 0)
  }

  test("eviction") {
    val ctx = new Ctx {}
    import ctx._

    val f = Future.value(1234)
    cache.set("key", f)
    val Some(res1) = cache.get("key")
    assert(Await.result(res1) === 1234)
    assert(cache.evict("key", f))

    val Some(res2) = cache.get("key")
    assert(Await.result(res2) === "key".hashCode)
    assert(cacheLoaderCount === 1)
  }

  test("eviction should refuse to evict incorrectly") {
    val ctx = new Ctx {}
    import ctx._

    val f = Future.value(1234)
    cache.set("key", f)
    val Some(res1) = cache.get("key")
    assert(Await.result(res1) === 1234)
    assert(!cache.evict("key", Future.value(4)))

    val Some(res2) = cache.get("key")
    assert(Await.result(res2) === 1234)
    assert(cacheLoaderCount === 0)
  }

  test("don't update gettable keys") {
    val ctx = new Ctx {}
    import ctx._

    val f = Future.value(1234)
    cache.set("key", f)

    var mod = false
    val result = cache.getOrElseUpdate("key") {
      mod = true
      Future.value(321)
    }
    assert(Await.result(result) === 1234)
    assert(mod === false)
    assert(cacheLoaderCount === 0)
  }

  test("update if ungettable") {
    val ctx = new Ctx {}
    import ctx._

    val result = cache.getOrElseUpdate("key") { Future.value(1234) }
    assert(Await.result(result) === 1234)
    assert(cacheLoaderCount === 0)
  }
}
