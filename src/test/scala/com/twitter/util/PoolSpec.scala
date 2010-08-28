package com.twitter.util

import scala.collection.mutable
import org.specs.Specification
import com.twitter.util.TimeConversions._

object PoolSpec extends Specification {
  "SimplePool" should {
    "with a simple queue of items" >> {
      "it reseves items in FIFO order" in {
        val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
        val pool = new SimplePool(queue)
        pool.reserve()() mustEqual 1
        pool.reserve()() mustEqual 2
        pool.release(2)
        pool.reserve()() mustEqual 3
        pool.reserve()() mustEqual 2
        pool.release(1)
        pool.release(2)
        pool.release(3)
      }
    }

    "with an object factory and a health check" >> {
      val pool = new FactoryPool[Int](4) {
        var count = 0
        def makeItem() = { count += 1; Future(count) }
        def isHealthy(i: Int) = i % 2 == 0
      }

      "reserve & release" >> {
        pool.reserve()() mustEqual 2
        pool.reserve()() mustEqual 4
        pool.reserve()() mustEqual 6
        pool.reserve()() mustEqual 8
        val promise = pool.reserve()
        promise(1.millisecond) must throwA[TimeoutException]
        pool.release(8)
        pool.release(6)
        promise() mustEqual 8
        pool.reserve()() mustEqual 6
        pool.reserve()(1.millisecond) must throwA[TimeoutException]
      }

      "reserve & dispose" >> {
        pool.reserve()() mustEqual 2
        pool.reserve()() mustEqual 4
        pool.reserve()() mustEqual 6
        pool.reserve()() mustEqual 8
        pool.reserve()(1.millisecond) must throwA[TimeoutException]
        pool.dispose(2)
        pool.reserve()(1.millisecond) mustEqual 10
      }
    }
  }
}
