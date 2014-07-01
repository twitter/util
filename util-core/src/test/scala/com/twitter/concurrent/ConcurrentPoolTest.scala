package com.twitter.concurrent

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConcurrentPoolTest extends WordSpec {
  "reserve items" in {
    val pool = new ConcurrentPool[Int, Int]

    pool.put(1, 2)
    assert(pool.get(1) === Some(2))
    assert(pool.get(1) === None)
  }

  "yield items in FIFO order" in {
    val pool = new ConcurrentPool[Int, Int]

    for (i <- 0 until 10)
      pool.put(1, i)

    for (i <- 0 until 10)    
      assert(pool.get(1) === Some(i))

    assert(pool.get(1) === None)
  }

  "kill empty lists" in {
    val pool = new ConcurrentPool[Int, Int]

    pool.put(1, 1)
    assert(pool.get(1) === Some(1))

    assert(pool.map.containsKey(1) === false)
    assert(pool.deathQueue.size === 1)

    pool.put(2, 1)
    assert(pool.deathQueue.size === 0)
  }

  // Can't really test the race condition case :-/
}
