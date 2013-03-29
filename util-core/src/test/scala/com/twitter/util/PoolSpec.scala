package com.twitter.util

import scala.collection.mutable
import org.specs.SpecificationWithJUnit
import com.twitter.conversions.time._

class PoolSpec extends SpecificationWithJUnit {
  "SimplePool" should {
    "with a simple queue of items" >> {
      "it reseves items in FIFO order" in {
        val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
        val pool = new SimplePool(queue)
        Await.result(pool.reserve()) mustEqual 1
        Await.result(pool.reserve()) mustEqual 2
        pool.release(2)
        Await.result(pool.reserve()) mustEqual 3
        Await.result(pool.reserve()) mustEqual 2
        pool.release(1)
        pool.release(2)
        pool.release(3)
      }
    }

    "with an object factory and a health check" >> {
      var count = 0
      val pool = new FactoryPool[Int](4) {
        def makeItem() = { count += 1; Future(count) }
        def isHealthy(i: Int) = i % 2 == 0
      }

      "reserve & release" >> {
        Await.result(pool.reserve()) mustEqual 2
        Await.result(pool.reserve()) mustEqual 4
        Await.result(pool.reserve()) mustEqual 6
        Await.result(pool.reserve()) mustEqual 8
        val promise = pool.reserve()
        Await.result(promise, 1.millisecond) must throwA[TimeoutException]
        pool.release(8)
        pool.release(6)
        Await.result(promise) mustEqual 8
        Await.result(pool.reserve()) mustEqual 6
        Await.result(pool.reserve, 1.millisecond) must throwA[TimeoutException]
      }

      "reserve & dispose" >> {
        Await.result(pool.reserve()) mustEqual 2
        Await.result(pool.reserve()) mustEqual 4
        Await.result(pool.reserve()) mustEqual 6
        Await.result(pool.reserve()) mustEqual 8
        Await.result(pool.reserve(), 1.millisecond) must throwA[TimeoutException]
        pool.dispose(2)
        Await.result(pool.reserve(), 1.millisecond) mustEqual 10
      }
    }
  }
}
