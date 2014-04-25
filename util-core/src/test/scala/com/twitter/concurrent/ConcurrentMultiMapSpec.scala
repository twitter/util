// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.scalatest.{WordSpec, Matchers}

class ConcurrentMultiMapSpec extends WordSpec with Matchers {
  "behave like a multimap" in {
    val map = new ConcurrentMultiMap[Int, Int]
    map += 1 -> 2
    map += 1 -> 3
    map += 1 -> 4

    map.get(1) should contain theSameElementsAs(List(2, 3, 4))
    map.get(0) shouldBe empty
    map.get(2) shouldBe empty

    map += 0 -> 20
    map += 3 -> 30
    map += 10 -> 40

    map.get(1) should contain theSameElementsAs(List(2, 3, 4))
    map.get(0) should contain theSameElementsAs(List(20))
    map.get(2) shouldBe empty
    map.get(3) should contain theSameElementsAs(List(30))
    map.get(10) should contain theSameElementsAs(List(40))
    map.get(110) shouldBe empty
  }
}
