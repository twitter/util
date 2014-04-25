// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.scalatest.{WordSpec, Matchers}

class ConcurrentBijectionSpec extends WordSpec with Matchers {
  "maintain a bijective map" in {
    val b = new ConcurrentBijection[Int, Int]
    b ++= (1 -> 1) :: (2 -> 3) :: (100 -> 2) :: Nil

    b.get(1)        shouldEqual Some(1)
    b.getReverse(1) shouldEqual Some(1)

    b.get(2)        shouldEqual Some(3)
    b.getReverse(3) shouldEqual Some(2)

    b.get(100)      shouldEqual Some(2)
    b.getReverse(2) shouldEqual Some(100)
  }

  "maintain the bijective property" in {
    val b = new ConcurrentBijection[Int, Int]

    b += (1 -> 2)

    b.get(1)        shouldEqual Some(2)
    b.getReverse(2) shouldEqual Some(1)

    // Introduce a new forward mapping. This should  delete the old
    // one.
    b += (1 -> 3)
    b.getReverse(2) shouldEqual None
    b.get(1)        shouldEqual Some(3)
    b.getReverse(3) shouldEqual Some(1)
    
    // Now, introduce a new reverse mapping for 3, which should  kill
    // the existing 1 -> 3 mapping.
    b += (100 -> 3)
    b.getReverse(3) shouldEqual Some(100)
    b.get(1)        shouldEqual None  // the old forward mapping was killed.
    b.get(100)      shouldEqual Some(3)

  }

  "delete mappings" in {
    val b = new ConcurrentBijection[Int, Int]

    b += (1 -> 2)

    b.isEmpty shouldBe false
    b -= 1
    b.isEmpty shouldBe true
  }

  "iterate over mappings" in {
    val b = new ConcurrentBijection[Int, Int]

    for (i <- 0 until 100)
      b += (i -> i)

    b should contain theSameElementsAs(for (i <- 0 until 100) yield (i, i))
  }
}
