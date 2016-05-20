package com.twitter.cache

import com.twitter.util.{Future, Promise}
import org.scalatest.FunSuite

// TODO: should also check for races
abstract class AbstractFutureCacheTest extends FunSuite {

  def name: String

  def mkCtx(): Ctx

  trait Ctx {
    val value = Future.value("value")
    val cache: FutureCache[String, String]
  }

  final def interruptSafe(fCache: (String => Future[Int])) {
    val f = fCache("key")
    val exn = new Exception
    f.raise(exn)

    val f2 = fCache("key")
    val p = new Promise[Int]
    p.become(f2)

    assert(p.isInterrupted == None)
  }

  test("%s should get nothing when there's nothing" format name) {
    val ctx = mkCtx()
    import ctx._

    assert(cache.get("key") == None)
    assert(cache.size == 0)
  }

  test("%s should get something when something's set" format name) {
    val ctx = mkCtx()
    import ctx._

    assert(cache.get("key") == None)
    cache.set("key", value)
    assert(cache.get("key") == Some(value))
    assert(cache.size == 1)
  }

  test("%s should evict when something's set" format name) {
    val ctx = mkCtx()
    import ctx._

    assert(cache.size == 0)
    assert(cache.get("key") == None)
    cache.set("key", value)
    assert(cache.size == 1)
    assert(cache.get("key") == Some(value))
    cache.evict("key", value)
    assert(cache.size == 0)
    assert(cache.get("key") == None)
  }

  test("%s should refuse to evict incorrectly" format name) {
    val ctx = mkCtx()
    import ctx._

    assert(cache.get("key") == None)
    cache.set("key", value)
    assert(cache.get("key") == Some(value))
    cache.evict("key", Future.value("mu"))
    assert(cache.get("key") == Some(value))
    cache.evict("key", value)
    assert(cache.get("key") == None)
  }

  test("%s should not update if gettable" format name) {
    val ctx = mkCtx()
    import ctx._

    cache.set("key", value)

    var mod = false
    val result = cache.getOrElseUpdate("key") {
      mod = true
      Future.value("mu")
    }
    assert(result == value)
    assert(mod == false)
  }

  test("%s should update if ungettable" format name) {
    val ctx = mkCtx()
    import ctx._

    val result = cache.getOrElseUpdate("key") { value }
    assert(result.poll == value.poll)
    assert(cache.size == 1)
  }

  test("%s should report correct size" format name) {
    val ctx = mkCtx()
    import ctx._

    cache.set("key", value)
    cache.set("key2", value)
    cache.set("key3", value)
    assert(cache.size == 3)
  }
}
