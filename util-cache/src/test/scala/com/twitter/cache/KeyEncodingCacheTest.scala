package com.twitter.cache

import java.util.concurrent.ConcurrentHashMap
import com.twitter.util.Future
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KeyEncodingCacheTest extends AbstractFutureCacheTest {
  def name: String = "KeyEncodingCache"

  def mkCtx(): Ctx = new Ctx {
    val underlyingMap: ConcurrentHashMap[Int, Future[String]] = new ConcurrentHashMap()
    val underlyingCache: FutureCache[Int, String] = new ConcurrentMapCache(underlyingMap)
    val cache: FutureCache[String, String] =
      new KeyEncodingCache({ num: String =>
        num.hashCode
      }, underlyingCache)
  }
}
