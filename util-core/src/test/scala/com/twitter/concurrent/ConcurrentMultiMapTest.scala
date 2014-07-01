// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConcurrentMultiMapTest extends WordSpec {
  "behave like a multimap" in {
    val map = new ConcurrentMultiMap[Int, Int]
    map += 1 -> 2
    map += 1 -> 3
    map += 1 -> 4

    assert(map.get(1) === List(2, 3, 4))
    assert(map.get(0) === List())
    assert(map.get(2) === List())

    map += 0 -> 20
    map += 3 -> 30
    map += 10 -> 40

    assert(map.get(1) === List(2, 3, 4))
    assert(map.get(0) === List(20))
    assert(map.get(2) === List())
    assert(map.get(3) === List(30))
    assert(map.get(10) === List(40))
    assert(map.get(110) === List())
  }
}
