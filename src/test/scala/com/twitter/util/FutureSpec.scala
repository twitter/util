package com.twitter.util

import org.specs.Specification

object FutureSpec extends Specification {
  "Future" should {
    "map" in {
      val f = Future(1) map { x => x + 1 }
      f() mustEqual 2
    }

    "flatMap" in {
      val f = Future(1) flatMap { x => Future(x + 1) }
      f() mustEqual 2
    }

    "filter" in {
      val f = Future(1) filter { x => x % 2 == 1 }
      f() mustEqual 1
      // val g = Future(1) filter { x => x % 2 == 0 }
      // g() mustEqual None // FIXME
    }

    "respond/foreach" in {
      var wasCalledWith: Option[Int] = None
      val f = Future(1)
      f respond { i =>
        wasCalledWith = Some(i)
      }
      wasCalledWith mustEqual Some(1)
    }
  }

  "Promise" should {
    "buffer computations until the result is set" in {
      var wasCalledWith: Option[Int] = None
      val f = new Promise[Int]
      f respond { i =>
        wasCalledWith = Some(i)
      }
      wasCalledWith mustEqual None
      f()= 1
      wasCalledWith mustEqual Some(1)
    }
  }
}