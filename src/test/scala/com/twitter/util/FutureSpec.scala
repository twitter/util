package com.twitter.util

import org.specs.Specification

object FutureSpec extends Specification {
  "Future" should {
    "normal values" >> {
      "map" in {
        val f = Future(1) map { x => x + 1 }
        f() mustEqual 2
      }

      "flatMap" in {
        val f = Future(1) flatMap { x => Future(x + 1) }
        f() mustEqual 2
      }

      "foreach" in {
        var wasCalledWith: Option[Int] = None
        val f = Future(1)
        f foreach { i =>
          wasCalledWith = Some(i)
        }
        wasCalledWith mustEqual Some(1)
      }

      "respond" in {
        var wasCalledWith: Option[Int] = None
        val f = Future(1)
        f respond {
          case Return(i) => wasCalledWith = Some(i)
          case Throw(e) => fail(e.toString)
        }
        wasCalledWith mustEqual Some(1)
      }
    }

    "exceptional values" in {
      val e = new Exception

      "Future() handles exceptions" in {
        val f = Future[Int] { throw e }
        f() must throwA(e)
      }
    }
  }

  "Promise" should {
    "buffer computations until the result is set" in {
      var wasCalledWith: Option[Int] = None
      val f = new Promise[Int]
      f foreach { i =>
        wasCalledWith = Some(i)
      }
      wasCalledWith mustEqual None
      f()= Return(1)
      wasCalledWith mustEqual Some(1)
    }
  }
}