package com.twitter.concurrent

import org.specs.SpecificationWithJUnit

class ConcurrentPoolSpec extends SpecificationWithJUnit {
  "reserve items" in {
    val pool = new ConcurrentPool[Int, Int]

    pool.put(1, 2)
    pool.get(1) must beSome(2)
    pool.get(1) must beNone
  }

  "yield items in FIFO order" in {
    val pool = new ConcurrentPool[Int, Int]

    for (i <- 0 until 10)
      pool.put(1, i)

    for (i <- 0 until 10)    
      pool.get(1) must beSome(i)

    pool.get(1) must beNone
  }

  "kill empty lists" in {
    val pool = new ConcurrentPool[Int, Int]

    pool.put(1, 1)
    pool.get(1) must beSome(1)

    pool.map.containsKey(1) must beFalse
    pool.deathQueue.size must be_==(1)

    pool.put(2, 1)
    pool.deathQueue.size must be_==(0)
  }

  // Can't really test the race condition case :-/
}
