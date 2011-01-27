package com.twitter.concurrent

import org.specs.Specification

object AsyncMutexSpec extends Specification {
  "AsyncMutext" should {
    "admit only one operation at a time" in {
      val m = new AsyncMutex

      val a0 = m.acquire()
      val a1 = m.acquire()

      a0.isDefined must beTrue
      a1.isDefined must beFalse

      a0()()                     // satisfy operation 0
      a1.isDefined must beTrue   // 1 now available

      val a2 = m.acquire()
      a2.isDefined must beFalse
      a1()()
      a2.isDefined must beTrue
    }
  }
}
