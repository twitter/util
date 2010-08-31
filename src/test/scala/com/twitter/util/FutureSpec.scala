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

        val e = new Exception
        val g = Future[Exception, Int](throw e) flatMap { x => Future(x + 1) }
        g() must throwA(e)
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
        val f = Future[Exception, Int] { throw e }
        f() must throwA(e)
      }
    }
  }

  "Promise" should {
    "buffer computations until the result is set" in {
      var wasCalledWith: Option[Int] = None
      val f = new Promise[Throwable, Int]
      f foreach { i =>
        wasCalledWith = Some(i)
      }
      wasCalledWith mustEqual None
      f()= Return(1)
      wasCalledWith mustEqual Some(1)
    }

    "flatMap" in {
      val p1 = new Promise[Throwable, Int]
      val p2 = new Promise[Throwable, Int]
      val p3 = p1 flatMap { x => p2 }
      p3 respond { x =>
        println(x)
      }
      p1() = Return(1)
    }
  }
}