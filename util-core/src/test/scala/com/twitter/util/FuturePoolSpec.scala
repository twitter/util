package com.twitter.util

import org.specs.Specification
import java.util.concurrent._

object FuturePoolSpec extends Specification {
  "FuturePool" should {
    "dispatch to another thread" in {
      val i = new atomic.AtomicInteger(0)
      val futures = FuturePool(Executors.newFixedThreadPool(5))
      val future: Future[Int] = futures { Thread.sleep(50); i.incrementAndGet() }
      i.get mustEqual 0
      future.get() mustEqual 1
      i.get mustEqual 1
    }
  }
}
