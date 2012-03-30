// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.specs.SpecificationWithJUnit

class ConcurrentBijectionSpec extends SpecificationWithJUnit {
  "maintain a bijective map" in {
    val b = new ConcurrentBijection[Int, Int]
    b ++= (1 -> 1) :: (2 -> 3) :: (100 -> 2) :: Nil

    b.get(1)        must beSome(1)
    b.getReverse(1) must beSome(1)

    b.get(2)        must beSome(3)
    b.getReverse(3) must beSome(2)

    b.get(100)      must beSome(2)
    b.getReverse(2) must beSome(100)
  }

  "maintain the bijective property" in {
    val b = new ConcurrentBijection[Int, Int]

    b += (1 -> 2)

    b.get(1)        must beSome(2)
    b.getReverse(2) must beSome(1)

    // Introduce a new forward mapping. This should delete the old
    // one.
    b += (1 -> 3)
    b.getReverse(2) must beNone
    b.get(1)        must beSome(3)
    b.getReverse(3) must beSome(1)
    
    // Now, introduce a new reverse mapping for 3, which should kill
    // the existing 1 -> 3 mapping.
    b += (100 -> 3)
    b.getReverse(3) must beSome(100)
    b.get(1)        must beNone  // the old forward mapping was killed.
    b.get(100)      must beSome(3)

  }

  "delete mappings" in {
    val b = new ConcurrentBijection[Int, Int]

    b += (1 -> 2)

    b.isEmpty must beFalse
    b -= 1
    b.isEmpty must beTrue
  }

  "iterate over mappings" in {
    val b = new ConcurrentBijection[Int, Int]

    for (i <- 0 until 100)
      b += (i -> i)

    b must haveTheSameElementsAs(for (i <- 0 until 100) yield (i, i))
  }
}
