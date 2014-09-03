package com.twitter.cache

import com.twitter.util.Future
import java.util.concurrent.ConcurrentHashMap
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConcurrentMapCacheTest extends AbstractFutureCacheTest {
  def name: String = "ConcurrentMapCache"

  def mkCtx(): Ctx = new Ctx {
    val map = new ConcurrentHashMap[String, Future[String]]()
    val cache = new ConcurrentMapCache[String, String](map)
  }
}
