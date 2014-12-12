package com.twitter.cache

import com.twitter.util.{Promise, Future}
import java.util.concurrent.ConcurrentHashMap
import org.junit.runner.RunWith
import org.mockito.Mockito.{verify, never}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class EvictingCacheTest extends FunSuite with MockitoSugar {
  test("EvictingCache should evict on failed futures for set") {
    val cache = mock[FutureCache[String, String]]
    val fCache = new EvictingCache(cache)
    val p = Promise[String]
    fCache.set("key", p)
    verify(cache).set("key", p)
    p.setException(new Exception)
    verify(cache).evict("key", p)
  }

  test("EvictingCache should keep satisfied futures for set") {
    val cache = mock[FutureCache[String, String]]
    val fCache = new EvictingCache(cache)
    val p = Promise[String]
    fCache.set("key", p)
    verify(cache).set("key", p)
    p.setValue("value")
    verify(cache, never).evict("key", p)
  }

  test("EvictingCache should evict on failed futures for getOrElseUpdate") {
    val map = new ConcurrentHashMap[String, Future[String]]()
    val cache = new ConcurrentMapCache(map)
    val fCache = new EvictingCache(cache)
    val p = Promise[String]
    assert(fCache.getOrElseUpdate("key")(p).poll === p.poll)
    p.setException(new Exception)
    assert(fCache.get("key") === None)
  }

  test("EvictingCache should keep satisfied futures for getOrElseUpdate") {
    val map = new ConcurrentHashMap[String, Future[String]]()
    val cache = new ConcurrentMapCache(map)
    val fCache = new EvictingCache(cache)
    val p = Promise[String]
    assert(fCache.getOrElseUpdate("key")(p).poll === p.poll)
    p.setValue("value")
    assert(fCache.get("key").map(_.poll) === Some(p.poll))
  }
}
