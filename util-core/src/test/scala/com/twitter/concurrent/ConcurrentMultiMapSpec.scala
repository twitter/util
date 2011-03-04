// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.specs.Specification

object ConcurrentMultiMapSpec extends Specification {
  "behave like a multimap" in {
    val map = new ConcurrentMultiMap[Int, Int]
    map += 1 -> 2
    map += 1 -> 3
    map += 1 -> 4

    map.get(1) must haveTheSameElementsAs(List(2, 3, 4))
    map.get(0) must beEmpty
    map.get(2) must beEmpty

    map += 0 -> 20
    map += 3 -> 30
    map += 10 -> 40

    map.get(1) must haveTheSameElementsAs(List(2, 3, 4))
    map.get(0) must haveTheSameElementsAs(List(20))
    map.get(2) must beEmpty
    map.get(3) must haveTheSameElementsAs(List(30))
    map.get(10) must haveTheSameElementsAs(List(40))
    map.get(110) must beEmpty
  }
}
