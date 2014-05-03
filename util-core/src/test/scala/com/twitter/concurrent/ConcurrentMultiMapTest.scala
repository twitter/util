// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConcurrentMultiMapTest extends WordSpec with ShouldMatchers {
  "behave like a multimap" in {
    val map = new ConcurrentMultiMap[Int, Int]
    map += 1 -> 2
    map += 1 -> 3
    map += 1 -> 4

    map.get(1) shouldEqual List(2, 3, 4)
    map.get(0) shouldEqual List()
    map.get(2) shouldEqual List()

    map += 0 -> 20
    map += 3 -> 30
    map += 10 -> 40

    map.get(1) shouldEqual List(2, 3, 4)
    map.get(0) shouldEqual List(20)
    map.get(2) shouldEqual List()
    map.get(3) shouldEqual List(30)
    map.get(10) shouldEqual List(40)
    map.get(110) shouldEqual List()
  }
}
