package com.twitter.cache

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future}
import org.scalatest.FunSuite

abstract class AbstractLoadingFutureCacheTest extends FunSuite {
  // NB we can't reuse AbstractFutureCacheTest since
  // loading cache semantics are sufficiently unique
  // to merit distinct tests.

  def name: String

  trait Ctx {
    var cacheLoaderCount = 0
    val cache: FutureCache[String, Int] with (String => Future[Int])
  }

  def mkCtx(): Ctx

  test(s"$name should return CacheLoader result for unset keys") {
    val ctx = mkCtx()
    import ctx._
    val Some(res) = cache.get("key")
    assert(Await.result(res, 2.seconds) == "key".hashCode)
    assert(cacheLoaderCount == 1)
  }

  test(s"$name should call CacheLoader one time for non-evicted keys") {
    val ctx = mkCtx()
    import ctx._
    val Some(res) = cache.get("key")
    val Some(res2) = cache.get("key")
    val Some(res3) = cache.get("key")
    assert(Await.result(res, 2.seconds) == "key".hashCode)
    assert(Await.result(res2, 2.seconds) == "key".hashCode)
    assert(Await.result(res3, 2.seconds) == "key".hashCode)
    assert(cacheLoaderCount == 1)
  }

  test(s"$name should return set values") {
    val ctx = mkCtx()
    import ctx._

    cache.set("key", Future.value(1234))
    val Some(res) = cache.get("key")
    val res2 = cache("key")

    assert(Await.result(res, 2.seconds) == 1234)
    assert(Await.result(res2, 2.seconds) == 1234)
    assert(cacheLoaderCount == 0)
  }

  test(s"$name should evict") {
    val ctx = mkCtx()
    import ctx._

    val f = Future.value(1234)
    cache.set("key", f)
    val Some(res1) = cache.get("key")
    assert(Await.result(res1, 2.seconds) == 1234)
    assert(cache.evict("key", f))

    val Some(res2) = cache.get("key")
    assert(Await.result(res2, 2.seconds) == "key".hashCode)
    assert(cacheLoaderCount == 1)
  }

  test(s"$name eviction should refuse to evict incorrectly") {
    val ctx = mkCtx()
    import ctx._

    val f = Future.value(1234)
    cache.set("key", f)
    val Some(res1) = cache.get("key")
    assert(Await.result(res1, 2.seconds) == 1234)
    assert(!cache.evict("key", Future.value(4)))

    val Some(res2) = cache.get("key")
    assert(Await.result(res2, 2.seconds) == 1234)
    assert(cacheLoaderCount == 0)
  }

  test(s"$name shouldn't update gettable keys") {
    val ctx = mkCtx()
    import ctx._

    val f = Future.value(1234)
    cache.set("key", f)

    var mod = false
    val result = cache.getOrElseUpdate("key") {
      mod = true
      Future.value(321)
    }
    assert(Await.result(result, 2.seconds) == 1234)
    assert(mod == false)
    assert(cacheLoaderCount == 0)
  }

  test(s"$name should update if ungettable") {
    val ctx = mkCtx()
    import ctx._

    val result = cache.getOrElseUpdate("key") { Future.value(1234) }
    assert(Await.result(result, 2.seconds) == 1234)
    assert(cacheLoaderCount == 0)
  }
}
