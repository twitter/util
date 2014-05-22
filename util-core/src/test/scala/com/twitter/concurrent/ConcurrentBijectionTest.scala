// Copyright 2010 Twitter, Inc.

package com.twitter.concurrent

import org.scalatest.WordSpec

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConcurrentBijectionTest extends WordSpec {
  "maintain a bijective map" in {
    val b = new ConcurrentBijection[Int, Int]
    b ++= (1 -> 1) :: (2 -> 3) :: (100 -> 2) :: Nil

    assert(b.get(1)        === Some(1))
    assert(b.getReverse(1) === Some(1))

    assert(b.get(2)        === Some(3))
    assert(b.getReverse(3) === Some(2))

    assert(b.get(100)      === Some(2))
    assert(b.getReverse(2) === Some(100))
  }

  "maintain the bijective property" in {
    val b = new ConcurrentBijection[Int, Int]

    b += (1 -> 2)

    assert(b.get(1)        === Some(2))
    assert(b.getReverse(2) === Some(1))

    // Introduce a new forward mapping. This should delete the old
    // one.
    b += (1 -> 3)
    assert(b.getReverse(2) === None)
    assert(b.get(1)        === Some(3))
    assert(b.getReverse(3) === Some(1))
    
    // Now, introduce a new reverse mapping for 3, which should kill
    // the existing 1 -> 3 mapping.
    b += (100 -> 3)
    assert(b.getReverse(3) === Some(100))
    assert(b.get(1)        === None)  // the old forward mapping was killed.
    assert(b.get(100)      === Some(3))

  }

  "delete mappings" in {
    val b = new ConcurrentBijection[Int, Int]

    b += (1 -> 2)

    assert(b.isEmpty === false)
    b -= 1
    assert(b.isEmpty === true)
  }

  "iterate over mappings" in {
    val b = new ConcurrentBijection[Int, Int]

    for (i <- 0 until 100)
      b += (i -> i)

    assert(b.toSet === (for (i <- 0 until 100) yield (i, i)).toSet)
  }
}
