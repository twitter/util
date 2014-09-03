package com.twitter.cache.guava

import com.google.common.cache.CacheBuilder
import com.twitter.cache.AbstractFutureCacheTest
import com.twitter.util.Future
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GuavaCacheTest extends AbstractFutureCacheTest {
  def name: String = "GuavaCache"

  def mkCtx(): Ctx = new Ctx {
    val guava = CacheBuilder.newBuilder().build[String, Future[String]]()
    val cache = new GuavaCache[String, String](guava)
  }
}
