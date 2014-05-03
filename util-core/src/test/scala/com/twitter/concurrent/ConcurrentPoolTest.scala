package com.twitter.concurrent

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConcurrentPoolTest extends WordSpec with ShouldMatchers {
  "reserve items" in {
    val pool = new ConcurrentPool[Int, Int]

    pool.put(1, 2)
    pool.get(1) shouldEqual Some(2)
    pool.get(1) shouldEqual None
  }

  "yield items in FIFO order" in {
    val pool = new ConcurrentPool[Int, Int]

    for (i <- 0 until 10)
      pool.put(1, i)

    for (i <- 0 until 10)    
      pool.get(1) shouldEqual Some(i)

    pool.get(1) shouldEqual None
  }

  "kill empty lists" in {
    val pool = new ConcurrentPool[Int, Int]

    pool.put(1, 1)
    pool.get(1) shouldEqual Some(1)

    pool.map.containsKey(1) shouldEqual false
    pool.deathQueue.size shouldEqual(1)

    pool.put(2, 1)
    pool.deathQueue.size shouldEqual(0)
  }

  // Can't really test the race condition case :-/
}
