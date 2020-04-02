package com.twitter.finagle.stats

import org.scalatest.FunSuite
import scala.jdk.CollectionConverters._

class CachedRegexTest extends FunSuite {
  test("Lets through things it should let through") {
    val cachedRegex = new CachedRegex("foo".r)
    val original: Map[String, Number] = Map("bar" -> 3)
    assert(cachedRegex(original) == original)
    assert(cachedRegex.regexMatchCache.asScala == Map("bar" -> false))
  }

  test("Doesn't let through things it shouldn't let through") {
    val cachedRegex = new CachedRegex("foo".r)
    val original: Map[String, Number] = Map("foo" -> 3)
    assert(cachedRegex(original) == Map.empty)
    assert(cachedRegex.regexMatchCache.asScala == Map("foo" -> true))
  }

  test("Keeps track of things that are repeated") {
    val cachedRegex = new CachedRegex("foo".r)
    val original: Map[String, Number] = Map("foo" -> 3)
    assert(cachedRegex(original) == Map.empty)
    assert(cachedRegex.regexMatchCache.asScala == Map("foo" -> true))

    assert(cachedRegex(original) == Map.empty)
    assert(cachedRegex.regexMatchCache.asScala == Map("foo" -> true))
  }

  test("Strips keys that don't reappear") {
    val cachedRegex = new CachedRegex("foo".r)
    val original: Map[String, Number] = Map("foo" -> 3)
    assert(cachedRegex(original) == Map.empty)
    assert(cachedRegex.regexMatchCache.asScala == Map("foo" -> true))

    val updated: Map[String, Number] = Map("bar" -> 4)
    assert(cachedRegex(updated) == updated)
    assert(cachedRegex.regexMatchCache.asScala == Map("bar" -> false))
  }
}
