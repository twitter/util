package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import com.twitter.util.Await

class AsyncMutexSpec extends SpecificationWithJUnit {
  "AsyncMutex" should {
    "admit only one operation at a time" in {
      val m = new AsyncMutex

      val a0 = m.acquire()
      val a1 = m.acquire()

      a0.isDefined must beTrue
      a1.isDefined must beFalse

      Await.result(a0).release()             // satisfy operation 0
      a1.isDefined must beTrue   // 1 now available

      val a2 = m.acquire()
      a2.isDefined must beFalse
      Await.result(a1).release()             // satisfy operation 1
      a2.isDefined must beTrue
    }
  }
}
